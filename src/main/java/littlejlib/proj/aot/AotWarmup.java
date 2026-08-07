package littlejlib.proj.aot;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Loading enough of the application that an AOT cache trained on this run is worth having.
 * <p>
 * The JVM assembles the cache from what a training run actually loaded, so a cache trained on
 * `proj --init` is close to useless and measurably WORSE than none at all — naming a cache also
 * suppresses the default CDS archive, so a thin cache loses you that too.
 * <p>
 * Class loading and linking is the expensive part and it is what the cache stores, so this
 * deliberately force-loads rather than executes. Executing would mean opening the index, starting
 * JavaFX and touching the daemon — slow, and side-effecting, on a run the user did not ask for.
 * Loading a class is enough to get it cached and cannot mutate anything.
 */
public final class AotWarmup {

    private AotWarmup() { }

    /** Everything here is startup-critical and expensive to load. */
    static final List<String> PREFIXES = List.of(
            "littlejlib/proj/",
            "org/jline/",
            "picocli/",
            "com/arcadedb/",
            "javafx/",
            "com/sun/javafx/",
            "com/google/gson/",
            "com/fasterxml/jackson/",
            "xyz/jphil/");

    static final int MAX_CLASSES = 12_000;
    static final long BUDGET_MS = 45_000;

    public static int run(java.io.PrintStream log) {
        var jar = jarPath();
        if (jar == null) {
            log.println("[warmup] not running from a jar; nothing to preload");
            return 0;
        }
        var loaded = 0;
        var deadline = System.currentTimeMillis() + BUDGET_MS;
        var loader = AotWarmup.class.getClassLoader();

        try (var jf = new JarFile(jar.toFile())) {
            var entries = jf.entries();
            while (entries.hasMoreElements() && loaded < MAX_CLASSES) {
                if (System.currentTimeMillis() > deadline) {
                    log.println("[warmup] time budget reached");
                    break;
                }
                var name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.contains("module-info")
                        || name.contains("package-info")) continue;
                if (PREFIXES.stream().noneMatch(name::startsWith)) continue;
                var cls = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    // initialize=false: link it, do not run static initialisers. Static init can
                    // start threads, open files and pop windows, none of which belongs in a
                    // warmup pass.
                    Class.forName(cls, false, loader);
                    loaded++;
                } catch (Throwable t) {
                    // A class that will not load is exactly the sort that must not fail the run.
                }
            }
        } catch (Exception e) {
            log.println("[warmup] could not read jar: " + e.getMessage());
        }

        exercise(log);
        log.println("[warmup] preloaded " + loaded + " classes");
        return loaded;
    }

    /** The few things worth genuinely running, because they dominate a real startup. */
    static void exercise(java.io.PrintStream log) {
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

    public static Path jarPath() {
        try {
            var src = AotWarmup.class.getProtectionDomain().getCodeSource();
            if (src == null) return null;
            var p = Path.of(src.getLocation().toURI());
            return p.toString().endsWith(".jar") ? p : null;
        } catch (Exception e) {
            return null;
        }
    }
}
