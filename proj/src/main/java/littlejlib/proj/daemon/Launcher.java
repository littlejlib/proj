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

    /** Daemon-only: the client's own flags are not capped, since every CLI call is short-lived. Same shape as jacli-daemon. */
    static final java.util.List<String> MEMORY_CAPS = java.util.List.of(
            "-Xmx768m", "-XX:+UseSerialGC", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30",
            "-XX:MaxMetaspaceSize=256m", "-XX:MaxDirectMemorySize=256m", "-XX:ReservedCodeCacheSize=96m");

    static boolean isMemoryFlag(String a) {
        return a.startsWith("-Xmx") || a.startsWith("-Xms") || a.startsWith("-XX:MaxHeap")
                || a.startsWith("-XX:MinHeap") || a.startsWith("-XX:MaxMetaspace")
                || a.startsWith("-XX:MaxDirect") || a.startsWith("-XX:ReservedCodeCache")
                || a.startsWith("-XX:+Use") && a.endsWith("GC");
    }

    public static Info startAndWait(Consumer<String> log) throws java.io.IOException {
        var jar = Build.jar();
        if (jar == null) throw new java.io.IOException(
                "running from classes, not a jar - start the daemon by hand with --daemon");

        var command = new ArrayList<String>();
        command.add(javaBinary());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> !a.startsWith("-agentlib") && !a.startsWith("-javaagent")
                        && !a.startsWith("-XX:AOTCacheOutput") && !isMemoryFlag(a))
                .forEach(command::add);
        command.addAll(MEMORY_CAPS);
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
