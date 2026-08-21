package littlejlib.proj.aot;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * What proj loads so an AOT cache trained on a run is worth having.
 *
 * <p>The mechanism moved to {@code littlejlib-aot} (in {@code littlejlib/jr/aot}) so every jr-launched tool
 * can have it; what stays here is the only part that was ever proj-specific — <b>which</b> code to load and
 * which of it is worth genuinely running. Same entry points as before, so nothing calling this changed.
 *
 * <p>The reason any of this exists: the JVM assembles the cache from what the training run actually loaded,
 * so a cache trained on {@code proj --init} is close to useless and measurably WORSE than none at all —
 * naming a cache also suppresses the default CDS archive, so a thin cache loses you that too. Measured on
 * {@code proj --help}: 1645ms none, 1837ms thin cache, 844ms full warmup.
 */
public final class AotWarmup {

    private AotWarmup() { }

    /**
     * A CURATED list, not everything in the jar — and that is a measured decision, not laziness.
     *
     * <p>{@code wholeApp()} was tried here and made proj <b>slower</b>: loading all ~81MB of the shaded jar
     * produced a 143MB cache and moved {@code proj --help} from a ~844ms median to ~2.8s. proj's jar carries
     * ArcadeDB, Lucene, Truffle/GraalJS, undertow and JavaFX, almost none of which a given command touches;
     * training on all of it fills the cache with classes that every later startup then has to map and
     * validate. <b>Cache size is not free at runtime</b> — the intuition that "it all ships anyway, so
     * loading it all costs nothing" holds for disk and not for startup.
     *
     * <p>So: name the packages that are startup-critical and expensive, and leave the rest to lazy loading.
     * {@code wholeApp()} suits an application whose jar is mostly code it actually runs.
     */
    static littlejlib.aot.AotWarmup config(PrintStream log) {
        return littlejlib.aot.AotWarmup.packages(
                        "littlejlib/proj/", "org/jline/", "picocli/", "com/arcadedb/",
                        "javafx/", "com/sun/javafx/", "com/google/gson/",
                        "com/fasterxml/jackson/", "xyz/jphil/")
                .maxClasses(12_000)
                // The default budget is sized for a normal run; a training run has the JVM dumping a cache
                // at the same time, which slows class loading by roughly 4x.
                .budget(java.time.Duration.ofMinutes(4))
                .log(log)
                .exercise(() -> exercise(log));
    }

    public static int run(PrintStream log) {
        return config(log).run();
    }

    /** No-op unless jr says this run is writing the cache. */
    public static boolean runIfTraining(PrintStream log) {
        return config(log).runIfTraining();
    }

    /** The few things worth genuinely running, because they dominate a real startup. */
    static void exercise(PrintStream log) {
        try {
            var settingsManager = new littlejlib.proj.SettingsManager();
            settingsManager.loadSettings();
        } catch (Throwable t) {
            log.println("[warmup] settings load skipped: " + t);
        }
        try (var terminal = org.jline.terminal.TerminalBuilder.builder().dumb(true).build()) {
            terminal.getWidth();
        } catch (Throwable t) {
            log.println("[warmup] terminal build skipped: " + t);
        }
        try {
            new picocli.CommandLine(new littlejlib.proj.Main())
                    .getCommandSpec().subcommands().size();
        } catch (Throwable t) {
            log.println("[warmup] picocli tree skipped: " + t);
        }
    }

    /** @deprecated use {@link littlejlib.aot.AotWarmup#ownCodeSource()}. */
    @Deprecated
    public static Path jarPath() {
        var p = littlejlib.aot.AotWarmup.ownCodeSource();
        return p != null && p.toString().endsWith(".jar") ? p : null;
    }
}
