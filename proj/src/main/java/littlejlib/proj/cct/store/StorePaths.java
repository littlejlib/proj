package littlejlib.proj.cct.store;

import module java.base;

/**
 * Where the claude-code store is, and where this half of proj keeps its own files.
 * <p>
 * The app home moved here from {@code ~/xyz-jphil/ccsearchconv} when this became part of proj: one
 * organisation, one project, one settings folder. The INDEX is not under here at all any more - it
 * is a database inside the ArcadeDB server proj already runs, which is what stopped the window and
 * the command line locking each other out.
 */
public final class StorePaths {

    private StorePaths() {}

    public static Path userHome() { return Path.of(System.getProperty("user.home")); }

    public static Path claudeHome() { return userHome().resolve(".claude"); }

    public static Path projectsDir() { return claudeHome().resolve("projects"); }

    public static Path historyFile() { return claudeHome().resolve("history.jsonl"); }

    public static Path fileHistoryDir() { return claudeHome().resolve("file-history"); }

    public static Path sessionEnvDir() { return claudeHome().resolve("session-env"); }

    public static Path tasksDir() { return claudeHome().resolve("tasks"); }

    public static Path sessionsDir() { return claudeHome().resolve("sessions"); }

    public static Path claudeJson() { return userHome().resolve(".claude.json"); }

    public static Path tempRoot() {
        var local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) return Path.of(local, "Temp", "claude");
        return Path.of(System.getProperty("java.io.tmpdir"), "claude");
    }

    public static Path appHome() {
        return littlejlib.proj.util.AppPaths.home().resolve("cctranscripts");
    }

    public static Path trashDir() { return appHome().resolve("trash"); }

    public static Path exportDir() { return appHome().resolve("exports"); }

    public static Path settingsFile() { return appHome().resolve("Settings.toml"); }

    public static Path scratchFile() { return appHome().resolve("scratch.txt"); }

    public static Path logFile() { return appHome().resolve("cctranscripts.log"); }

    public static void ensureAppHome() {
        try {
            Files.createDirectories(trashDir());
            Files.createDirectories(exportDir());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create app home " + appHome(), e);
        }
    }
}
