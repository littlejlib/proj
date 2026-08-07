package littlejlib.proj.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import littlejlib.proj.LogConfig;
import littlejlib.proj.StdoutChannel;
import littlejlib.proj.aot.AotBuilder;
import littlejlib.proj.aot.AotState;
import littlejlib.proj.aot.AotWarmup;

import java.nio.file.Path;

/** Inspecting and deliberately rebuilding the startup cache. */
public final class AotCommands {

    private AotCommands() { }

    static final java.io.PrintStream OUT = StdoutChannel.data();
    static final java.io.PrintStream ERR = LogConfig.err();

    @Command(name = "aot", description = "Inspect or rebuild the AOT startup cache",
             subcommands = { StatusCmd.class, CreateCmd.class, WarmupCmd.class })
    public static class AotCmd implements Runnable {
        @Override
        public void run() {
            new StatusCmd().run();
        }
    }

    @Command(name = "status", description = "Show the AOT cache state jr reported")
    public static class StatusCmd implements Runnable {
        @Override
        public void run() {
            var size = AotState.cacheSize();
            OUT.println("JR_AOT_STATE  : " + AotState.state());
            OUT.println("JR_LAUNCH_MODE: " + String.valueOf(System.getenv("JR_LAUNCH_MODE")));
            OUT.println("cache path    : " + AotState.cache());
            OUT.println("cache present : " + AotState.cacheExists()
                    + (size > 0 ? " (" + (size / 1024 / 1024) + " MB)" : ""));
            if (AotState.state().equals(AotState.OFF))
                ERR.println("\nAOT is off for this run. Launch via proj.exe, or check aot= in proj.jrc.");
        }
    }

    @Command(name = "create", description = "Rebuild the cache from a full warmup pass")
    public static class CreateCmd implements Runnable {
        @Option(names = "--cache", description = "Cache file to write (default: the one jr reported)")
        String cache;

        @Override
        public void run() {
            try {
                var target = cache != null ? Path.of(cache) : AotState.cache();
                var exit = AotBuilder.rebuild(target, ERR);
                if (exit == 0) ERR.println("AOT cache rebuilt: " + target);
            } catch (Exception e) {
                ERR.println("could not rebuild the AOT cache: " + e.getMessage());
            }
        }
    }

    /** The training payload. Only useful inside a run launched with -XX:AOTCacheOutput. */
    @Command(name = "warmup", description = "Load the application heavily (used to train the cache)")
    public static class WarmupCmd implements Runnable {
        @Override
        public void run() {
            AotWarmup.run(ERR);
            if (!AotState.creating())
                ERR.println("note: this run is not writing a cache (JR_AOT_STATE=" + AotState.state() + ")");
        }
    }
}
