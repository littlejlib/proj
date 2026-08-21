package littlejlib.proj.daemon;

import littlejlib.proj.util.AppPaths;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Starts and stops the resident half.
 * <p>
 * The daemon inherits this process's VM arguments, because proj.bat passes --enable-native-access
 * and several --add-opens that ArcadeDB needs; a daemon started without them fails in ways that
 * look nothing like a missing flag.
 */
public final class Launcher {

    private Launcher() { }

    public static final long START_WAIT_MS = 30_000, EXIT_WAIT_MS = 10_000, POLL_MS = 200;

    public static Info startAndWait(Consumer<String> log) throws java.io.IOException {
        var jar = Build.jar();
        if (jar == null) throw new java.io.IOException(
                "running from classes, not a jar - start the daemon by hand with --daemon");

        var command = new ArrayList<String>();
        command.add(javaBinary());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> !a.startsWith("-agentlib") && !a.startsWith("-javaagent")
                        && !a.startsWith("-XX:AOTCacheOutput"))
                .forEach(command::add);
        command.add("-jar");
        command.add(jar.getAbsolutePath());
        command.add("--daemon");

        Files.createDirectories(AppPaths.home());
        var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(AppPaths.daemonLog().toFile()));
        pb.start();
        log.accept("started a daemon, waiting for it to listen");

        var deadline = System.currentTimeMillis() + START_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            var info = Info.read();
            if (info != null && info.processAlive() && info.port > 0) return info;
            sleep();
        }
        throw new java.io.IOException("daemon did not start within " + START_WAIT_MS
                + "ms - see " + AppPaths.daemonLog());
    }

    public static void awaitExit(Info info, Consumer<String> log) {
        var deadline = System.currentTimeMillis() + EXIT_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!info.processAlive()) return;
            sleep();
        }
        log.accept("daemon pid " + info.pid + " did not exit, killing it");
        ProcessHandle.of(info.pid).ifPresent(ProcessHandle::destroyForcibly);
        Info.clear();
    }

    static String javaBinary() {
        var name = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return new File(new File(System.getProperty("java.home"), "bin"), name).getAbsolutePath();
    }

    static void sleep() {
        try {
            Thread.sleep(POLL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
