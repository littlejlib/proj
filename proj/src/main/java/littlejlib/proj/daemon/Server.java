package littlejlib.proj.daemon;

import littlejlib.proj.Settings;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.db.DB;
import littlejlib.proj.scanner.ProjectWatcher;
import littlejlib.proj.ui.JavaFXManager;
import littlejlib.proj.ui.PickerHost;
import littlejlib.proj.ui.ProjectPickerDialog;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The resident half: JavaFX warm, ArcadeDB open, the filesystem watcher running, a tray icon.
 * <p>
 * What it does NOT do is touch the caller's terminal. A selection comes back as a path and the
 * client acts on it, because PROJ_POSTRUN_BATCH belongs to the shell that ran the client.
 * <p>
 * Requests are served one at a time. There is one UI and one ArcadeDB, and neither wants two
 * callers at once; a request costs milliseconds unless a human is staring at the picker, and in
 * that case queueing is the correct behaviour anyway.
 */
public class Server {

    /** Guards the WINDOW: one picker or menu at a time, and it is held for as long as it is open. */
    final Object lock = new Object();

    /**
     * Guards QUERIES, and deliberately not the same object as {@link #lock}.
     * <p>
     * They shared one lock until 2026-08-21, and the consequence was that no command-line query
     * could be answered while the picker window was on screen - the window holds its lock for its
     * whole life, so `proj list` waited, gave up, opened the database itself and hit the exclusive
     * directory lock this process is holding. The advice it then printed was to quit the very window
     * the user was working in. Queries now serialise only against each other.
     */
    final Object queryLock = new Object();

    final TrayUi tray = new TrayUi();
    final WorkspaceAutoRefresh workspaces = new WorkspaceAutoRefresh();

    static final Duration IDLE_TRIM_AFTER = Duration.ofMinutes(2);

    ServerSocket serverSocket;
    ProjectWatcher watcher;
    volatile boolean running = true;
    static volatile Instant lastActivity = Instant.now();

    public void run() throws Exception {
        System.setProperty("java.awt.headless", "false");

        serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        var info = new Info(serverSocket.getLocalPort(), ProcessHandle.current().pid(), Build.STAMP);
        info.write();
        mark("listening on 127.0.0.1:" + info.port + "  build " + Build.STAMP);

        DB.get();
        mark("database open");
        JavaFXManager.getInstance().ensurePlatformInitialized();
        mark("javafx platform initialized");
        // Registering the watcher is now backgrounded - see startWatcher - so this marks only that
        // it was handed off, not that ~11,700 directories are already being watched.
        startWatcher();
        mark("watcher registration handed off");

        // Build the picker window now, hidden. Measured: the first Stage plus Scene costs about
        // two to four seconds even with the platform already up, and paying that at startup is the
        // difference between a daemon that helps and one that merely exists. A client that arrives
        // before this finishes still queues behind it on the FX thread - see PickerHost.prewarm's
        // own timing line for how much of the first-ever "open" this actually costs.
        var settings = new SettingsManager().loadSettings();
        if (settings != null) PickerHost.prewarm(browsePaths(settings), settings);
        mark("prewarm scheduled");

        tray.install(() -> new Thread(this::openPickerStandalone, "Tray-Open").start(),
                () -> new Thread(this::rescanAll, "Tray-Rescan").start(),
                this::shutdown);
        installSummonKey(settings);
        workspaces.start();
        tray.note("ready");
        IdleTrim.start(() -> PickerHost.isShowing() ? Instant.now() : lastActivity, IDLE_TRIM_AFTER);
        mark("accepting requests");

        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup, "proj-daemon-shutdown"));

        while (running) {
            try {
                var socket = serverSocket.accept();
                new Thread(() -> serve(socket), "proj-request").start();
            } catch (Exception e) {
                if (running) log("accept failed: " + e.getMessage());
            }
        }
    }

    /** Anything the daemon does on its own counts too, or the idle trim runs before a background walk and never after. */
    static void touched() {
        lastActivity = Instant.now();
    }

    static void mark(String what) {
        log(what + "   +" + littlejlib.proj.util.Boot.ms() + "ms");
        System.out.flush();
    }

    void serve(java.net.Socket socket) {
        try (socket) {
            var request = Protocol.readRequest(socket.getInputStream());
            Protocol.write(socket.getOutputStream(), handle(request));
        } catch (Exception e) {
            log("request failed: " + e.getMessage());
        } finally {
            touched();
        }
    }

    Response handle(Request request) {
        if (request == null) return Response.error(2, "empty request");

        var verb = request.verb();
        if ("--ping".equals(verb)) return Response.ok("proj daemon " + Build.STAMP + " pid " + ProcessHandle.current().pid());
        if ("--stop".equals(verb)) {
            new Thread(this::shutdown, "Stop-Requested").start();
            return Response.ok("stopping");
        }

        if (!Build.STAMP.equals(request.stamp)) return Response.stale(Build.STAMP);

        // Index queries are served here because the daemon holds ArcadeDB's exclusive directory
        // lock. A CLI process cannot open the same database while this one is alive, so the query
        // comes to the data rather than the other way round - and it must not queue behind an open
        // window, which is why this is outside the window lock. See queryLock.
        if ("query".equals(verb)) {
            synchronized (queryLock) {
                return query(request.args.subList(1, request.args.size()));
            }
        }

        synchronized (lock) {
            return switch (verb) {
                case "open", "o", "" -> openPicker(request.args != null && request.args.size() > 1
                        ? request.args.get(1) : null);
                // The main menu, for the same reason as the picker and one more. The picker it
                // leads to needs the ArcadeDB lock this process is already holding, so a menu shown
                // in the client can offer an option that cannot work; and the menu is a JavaFX
                // window, which is free here and costs a cold toolkit start anywhere else.
                case "menu" -> MenuService.serve(request, tray::note, this::openPicker);
                default -> Response.error(2, "the daemon does not serve '" + verb + "' yet");
            };
        }
    }

    Response openPicker() {
        return openPicker(null);
    }

    Response openPicker(String tab) {
        var settings = new SettingsManager().loadSettings();
        if (settings == null) return Response.error(3, "no settings; run proj once in a terminal to create them");

        tray.note("picker open");
        mark("picker open requested" + (tab == null ? "" : " on tab " + tab));
        var result = ProjectPickerDialog.show(browsePaths(settings), settings, tab);
        mark("picker window closed");
        tray.note("ready");

        if (result == null || result.absolutePath() == null) return Response.ok("Selection cancelled.");
        return Response.chose(result.absolutePath().toAbsolutePath().toString());
    }

    Response query(List<String> args) {
        var errSink = new java.io.StringWriter();
        try {
            var stdout = littlejlib.proj.cli.IndexAccess.serveInDaemon(args, errSink);
            var response = Response.ok(stdout);
            response.stderr = errSink.toString();
            return response;
        } catch (Exception e) {
            return Response.error(3, "query failed in the daemon: " + e.getMessage());
        }
    }

    /**
     * The summon gesture: a modifier tapped twice, heard through a low-level keyboard hook, from
     * inside whatever program the user is actually in.
     * <p>
     * This lives in the daemon because the daemon is the only part of proj that is running when
     * nobody has typed anything - and because the window it opens is already built and warm here.
     * A machine that refuses the hook loses nothing else: the tray icon and the command line are
     * unchanged, and the refusal is logged rather than thrown.
     */
    void installSummonKey(Settings settings) {
        var spec = settings == null ? null : settings.getGlobalHotkey();
        if (littlejlib.proj.util.GlobalHotkey.isOff(spec)) {
            log("summon key: off");
            return;
        }
        var installed = littlejlib.proj.util.GlobalHotkey.install(spec, this::summon);
        var name = littlejlib.proj.util.GlobalHotkey.describe(spec);
        // Reported either way, and the failure says WHY it is the interesting case: Windows refuses
        // to hand over a combination something else has already claimed, and the only symptom
        // otherwise is a key that does nothing.
        log(installed ? "summon key: " + name
                : "summon key: " + name + " could not be claimed - something else already owns it, "
                        + "or this platform has no hotkeys. Use the tray icon, or pick another in Settings");
    }

    /**
     * The summon key, which TOGGLES: the window goes away if it is already up.
     * <p>
     * One key for both directions because that is what a summon key is for - you press it without
     * looking to see whether the window is there, and a key that only ever opens leaves you hunting
     * for Esc when the answer was "not now". Hiding is exactly a cancel; the waiting caller gets a
     * null result and nothing is selected.
     * <p>
     * The showing check happens BEFORE the lock, and has to: while the picker is up,
     * {@link #openPickerStandalone} is holding that lock and blocked on a human, so a second summon
     * that tried to acquire it would deadlock instead of closing the window.
     */
    void summon() {
        if (PickerHost.isShowing()) {
            PickerHost.hide();
            return;
        }
        openPickerStandalone();
    }

    /**
     * The picker opened with nobody waiting on the answer - from the tray, or from the summon key.
     * <p>
     * A client shell gets a path back and changes into it. There is no shell here, and a process
     * cannot change the directory of one it did not start, so the only honest reading of "go here"
     * is a NEW terminal at that project. Picking a project from the tray previously did nothing at
     * all, which is what made the tray item look broken.
     */
    void openPickerStandalone() {
        synchronized (lock) {
            var settings = new SettingsManager().loadSettings();
            if (settings == null) return;

            tray.note("picker open");
            var result = ProjectPickerDialog.show(browsePaths(settings), settings);
            tray.note("ready");
            touched();

            if (result != null && result.absolutePath() != null) {
                littlejlib.proj.terminal.TerminalRunner.open(result.absolutePath(), settings);
            }
        }
    }

    void rescanAll() {
        tray.note("rescanning");
        restartWatcher();
        // The plain groups too. They have no watcher to restart, which is exactly why the one
        // gesture that means "everything is stale, go and look again" has to reach them.
        var settings = new SettingsManager().loadSettings();
        if (settings != null) {
            for (var group : littlejlib.proj.group.Groups.plain(settings)) {
                littlejlib.proj.workspace.WorkspaceRefresh.run(group, true, null, note -> log("  " + note));
            }
        }
        tray.note("ready");
    }

    static List<Path> browsePaths(Settings settings) {
        return settings.getBrowsePaths().stream().map(Paths::get).filter(Files::isDirectory).toList();
    }

    /**
     * Registering a {@code WatchKey} on every directory the scanner would descend into, across every
     * code base, is the single biggest cost in a cold daemon boot - measured at over five seconds
     * for ~11,700 directories across three bases (PRP 35), more than the JavaFX platform bootstrap
     * and the first Stage+Scene put together. Nothing needs it answered before the first window can
     * appear - a change missed in the few seconds before the watcher is live is caught by the next
     * scan or the watcher itself once it is - so the registration walk runs on its own thread and
     * {@link #run} moves straight on to warming the picker instead of queueing behind it.
     */
    void startWatcher() {
        var settings = new SettingsManager().loadSettings();
        if (settings == null) return;
        watcher = new ProjectWatcher(settings.getProjectScanningDepth()).onActivity(Server::touched);
        var paths = browsePaths(settings);
        new Thread(() -> {
            var t0 = littlejlib.proj.util.Boot.ms();
            watcher.start(paths);
            log("watching " + paths + "   (registration took " + (littlejlib.proj.util.Boot.ms() - t0) + "ms)");
        }, "ProjectWatcher-Register").start();
    }

    void restartWatcher() {
        if (watcher != null) watcher.stop();
        startWatcher();
    }

    public void shutdown() {
        running = false;
        cleanup();
        System.exit(0);
    }

    void cleanup() {
        try {
            littlejlib.proj.util.GlobalHotkey.uninstall();
            if (watcher != null) watcher.stop();
            workspaces.stop();
            PickerHost.dispose();
            tray.remove();
            if (serverSocket != null) serverSocket.close();
            Info.clear();
            DB.get().shutdown();
        } catch (Exception e) {
            log("cleanup: " + e.getMessage());
        }
    }

    static void log(String message) {
        System.out.println("[proj-daemon] " + message);
    }
}
