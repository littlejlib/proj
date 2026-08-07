package littlejlib.proj.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where this tool keeps its state. One place, so the daemon file, the settings and the database
 * cannot drift apart.
 */
public final class AppPaths {

    private AppPaths() { }

    public static Path home() {
        return Paths.get(System.getProperty("user.home"), "littlejlib", "proj");
    }

    public static Path daemonInfo() {
        return home().resolve("daemon.json");
    }

    public static Path daemonLog() {
        return home().resolve("daemon.log");
    }

    public static Path settings() {
        return home().resolve("Settings.toml");
    }

    public static Path legacySettings() {
        return home().resolve("Settings.xml");
    }

    public static Path arcadeDb() {
        return home().resolve("arcadedb");
    }

    public static Path prpTemplateCache() {
        return home().resolve("prp-template-cache");
    }
}
