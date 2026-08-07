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

    final Object lock = new Object();
    final TrayUi tray = new TrayUi();

    ServerSocket serverSocket;
    ProjectWatcher watcher;
    volatile boolean running = true;

    public void run() throws Exception {
        System.setProperty("java.awt.headless", "false");

        serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        var info = new Info(serverSocket.getLocalPort(), ProcessHandle.current().pid(), Build.STAMP);
        info.write();
        log("listening on 127.0.0.1:" + info.port + "  build " + Build.STAMP);

        DB.get();
        JavaFXManager.getInstance().ensurePlatformInitialized();
        startWatcher();

        // Build the picker window now, hidden. Measured: the first Stage plus Scene costs about
        // four seconds even with the platform already up, and paying that at startup is the
        // difference between a daemon that helps and one that merely exists.
        var settings = new SettingsManager().loadSettings();
        if (settings != null) PickerHost.prewarm(browsePaths(settings), settings);

        tray.install(() -> new Thread(this::openPickerFromTray, "Tray-Open").start(),
                () -> new Thread(this::rescanAll, "Tray-Rescan").start(),
                this::shutdown);
        tray.note("ready");

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

    void serve(java.net.Socket socket) {
        try (socket) {
            var request = Protocol.readRequest(socket.getInputStream());
            Protocol.write(socket.getOutputStream(), handle(request));
        } catch (Exception e) {
            log("request failed: " + e.getMessage());
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

        synchronized (lock) {
            return switch (verb) {
                case "open", "o", "" -> openPicker();
                // Read-only index queries are served here because the daemon holds ArcadeDB's
                // exclusive directory lock. A CLI process cannot open the same database while this
                // one is alive, so the query comes to the data rather than the other way round.
                case "query" -> query(request.args.subList(1, request.args.size()));
                default -> Response.error(2, "the daemon does not serve '" + verb + "' yet");
            };
        }
    }

    Response openPicker() {
        var settings = new SettingsManager().loadSettings();
        if (settings == null) return Response.error(3, "no settings; run proj once in a terminal to create them");

        tray.note("picker open");
        var result = ProjectPickerDialog.show(browsePaths(settings), settings);
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

    void openPickerFromTray() {
        synchronized (lock) {
            openPicker();
        }
    }

    void rescanAll() {
        tray.note("rescanning");
        restartWatcher();
        tray.note("ready");
    }

    static List<Path> browsePaths(Settings settings) {
        return settings.getBrowsePaths().stream().map(Paths::get).filter(Files::isDirectory).toList();
    }

    void startWatcher() {
        var settings = new SettingsManager().loadSettings();
        if (settings == null) return;
        watcher = new ProjectWatcher(settings.getProjectScanningDepth());
        watcher.start(browsePaths(settings));
        log("watching " + browsePaths(settings));
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
            if (watcher != null) watcher.stop();
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
