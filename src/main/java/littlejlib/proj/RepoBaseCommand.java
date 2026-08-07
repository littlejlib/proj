package littlejlib.proj;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manage coderepobases from the command line - the counterpart of the picker's +Base button.
 * <p>
 * The type is required on add rather than defaulted, because getting it wrong is exactly the
 * mistake that fills your results with noise or hides code you meant to see.
 */
@Command(name = "repobase", aliases = {"rb"},
        description = "List, add or remove coderepobases (directories holding many repositories)")
public class RepoBaseCommand implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "list | add | remove")
    String action = "list";

    @Parameters(index = "1", arity = "0..1", description = "Directory path, for add and remove")
    String path;

    @Option(names = {"--type", "-t"}, description = "primary | mirror | auxiliary   (required for add)")
    String type;

    @Override
    public void run() {
        var manager = new SettingsManager();
        var settings = manager.loadSettings();
        if (settings == null) {
            System.err.println("No settings yet. Run proj once to create them.");
            return;
        }

        switch (action == null ? "list" : action.toLowerCase()) {
            case "list" -> list(settings);
            case "add" -> add(manager, settings);
            case "remove", "rm" -> remove(manager, settings);
            default -> System.out.println("unknown action: " + action + "   (list | add | remove)");
        }
    }

    void list(Settings settings) {
        var bases = settings.getCodeRepoBases();
        if (bases.isEmpty()) {
            System.out.println("no coderepobases configured");
            return;
        }
        System.out.println(bases.size() + " coderepobase(s):");
        for (var b : bases) {
            System.out.println("  " + pad(b.getType().slug(), 10)
                    + (b.exists() ? "        " : " MISSING ") + b.getPath());
        }
        System.out.println();
        for (var t : CodeRepoBaseType.values()) System.out.println("  " + pad(t.slug(), 10) + t.description);
    }

    void add(SettingsManager manager, Settings settings) {
        if (path == null || path.isBlank()) {
            System.err.println("which directory? usage: proj repobase add <path> --type primary|mirror|auxiliary");
            return;
        }
        if (type == null || type.isBlank()) {
            System.err.println("--type is required, and deliberately has no default: "
                    + "primary | mirror | auxiliary");
            return;
        }
        var parsed = CodeRepoBaseType.parse(type, null);
        if (parsed == null) {
            System.err.println("unknown type '" + type + "'   (primary | mirror | auxiliary)");
            return;
        }
        var dir = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            System.err.println("not a directory: " + dir);
            return;
        }

        settings.addCodeRepoBase(dir.toString(), parsed);
        manager.saveSettings(settings);
        System.out.println("added " + parsed.slug() + " coderepobase: " + dir);
        System.out.println("it will be scanned the next time the picker opens; "
                + "run 'proj daemon restart' if a daemon is already running");
    }

    void remove(SettingsManager manager, Settings settings) {
        if (path == null || path.isBlank()) {
            System.err.println("which directory? usage: proj repobase remove <path>");
            return;
        }
        var dir = Path.of(path).toAbsolutePath().normalize();
        if (settings.removeCodeRepoBase(dir.toString())) {
            manager.saveSettings(settings);
            System.out.println("removed " + dir);
        } else {
            System.out.println("not configured: " + dir);
        }
    }

    static String pad(String s, int n) {
        return s.length() >= n ? s + " " : s + " ".repeat(n - s.length());
    }
}
