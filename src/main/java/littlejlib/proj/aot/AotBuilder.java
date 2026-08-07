package littlejlib.proj.aot;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Rebuilding the AOT cache deliberately, instead of hoping the next run happens to be a good
 * training run.
 * <p>
 * It has to be a child process: the JVM assembles the cache when the training process EXITS, so
 * a process cannot create a cache and then use it. The child is given the parent's VM arguments
 * so the cache matches the configuration it will later be read under.
 */
public final class AotBuilder {

    private AotBuilder() { }

    public static final String WARMUP_FLAG = "--aot-warmup";

    public static int rebuild(Path cache, java.io.PrintStream log) throws IOException {
        var jar = AotWarmup.jarPath();
        if (jar == null) throw new IOException("not running from a jar; nothing to build a cache from");
        if (cache == null) throw new IOException(
                "no cache path known. Run via proj.exe (jr exports JR_AOT_CACHE), or pass --cache <path>.");

        if (Files.exists(cache)) {
            Files.delete(cache);
            log.println("removed old cache " + cache);
        }

        var command = new ArrayList<String>();
        command.add(javaBinary());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(AotBuilder::keepForTraining)
                .forEach(command::add);
        // Full tiered, so C2 runs and its method profiles land in the cache. Nothing here caps the
        // compiler and nothing downstream does either, so this training run matches the runs that
        // will later read the cache.
        command.add("-XX:AOTCacheOutput=" + cache);
        command.add("-jar");
        command.add(jar.toString());
        command.add(WARMUP_FLAG);

        log.println("training: " + String.join(" ", command));
        var pb = new ProcessBuilder(command);
        // jr hands its flags to the JVM through JAVA_TOOL_OPTIONS, and a child JVM inherits it.
        // Left in place, a rebuild launched from a normal run would inherit that run's
        // -XX:AOTCache=<same file> alongside the -XX:AOTCacheOutput=<same file> added below --
        // reading and writing one cache in one JVM. Every flag needed is already on the command
        // line, so clear it.
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        try {
            var exit = pb.start().waitFor();
            if (exit != 0) log.println("training process exited with " + exit);
            return exit;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while training", e);
        }
    }

    static boolean keepForTraining(String arg) {
        return !arg.startsWith("-agentlib")
                && !arg.startsWith("-javaagent")
                && !arg.startsWith("-XX:AOTCache");
    }

    static String javaBinary() {
        var win = System.getProperty("os.name", "").toLowerCase().contains("win");
        return Path.of(System.getProperty("java.home"), "bin", win ? "java.exe" : "java").toString();
    }
}
