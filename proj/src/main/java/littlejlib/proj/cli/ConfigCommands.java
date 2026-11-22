package littlejlib.proj.cli;

import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import littlejlib.proj.Settings;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.config.SettingsCodec;
import littlejlib.proj.config.Toml;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Every setting, from a terminal, addressed by the same dotted key the file uses.
 * <p>
 * The settings window is fine for a person and useless to an agent, which cannot click anything;
 * hand-editing the TOML works but makes the caller responsible for quoting, for knowing the key
 * space, and for not corrupting the file. These commands go through the same codec the window does,
 * so a write is validated, normalised and re-commented exactly as if it had been saved from the UI.
 * <p>
 * Keys are literally the lines in the file: {@code scanDepth}, {@code agentCommand},
 * {@code codebase.primary}, {@code terminal.Tabby.open}, {@code ide.NetBeans}, {@code org.xyz-jphil}.
 * {@code proj config list} prints them, which makes the file its own documentation.
 */
public final class ConfigCommands {

    private ConfigCommands() { }

    static final java.io.PrintStream OUT = littlejlib.proj.StdoutChannel.data();
    static final java.io.PrintStream ERR = littlejlib.proj.LogConfig.err();
    static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Settings load() {
        var settings = new SettingsManager().loadSettings();
        if (settings == null) {
            ERR.println("no usable settings file at " + new SettingsManager().getSettingsFilePath());
            System.exit(4);
        }
        return settings;
    }

    /**
     * Saves, then says so if a daemon is holding an older copy.
     * <p>
     * The daemon reads settings when it starts and when it opens the picker, so a change made here
     * while it is running is real in the file and not yet real in the window. Saying nothing would
     * leave the caller to conclude the write had failed.
     */
    static void save(Settings settings, String what) {
        new SettingsManager().saveSettings(settings);
        OUT.println(what);
        if (littlejlib.proj.daemon.Client.ping() != null) {
            OUT.println("a daemon is running with the previous settings - "
                    + "run 'proj daemon restart' for it to pick this up");
        }
    }

    @Command(name = "list", aliases = "ls", description = "Print every setting as key = value")
    public static class ListCmd implements Runnable {

        @Option(names = "--json", description = "JSON instead of the file's own key = value form")
        boolean json;

        @Option(names = "--keys", description = "Just the key names, one per line")
        boolean keysOnly;

        @Override
        public void run() {
            var map = SettingsCodec.toMap(load());
            if (keysOnly) {
                map.keySet().forEach(OUT::println);
            } else if (json) {
                OUT.println(GSON.toJson(map));
            } else {
                map.forEach((k, v) -> OUT.println(k + " = " + Toml.value(v)));
            }
        }
    }

    @Command(name = "get", description = "Print one setting's value")
    public static class GetCmd implements Runnable {

        @Parameters(arity = "1", description = "Key, e.g. agentCommand or terminal.Tabby.open")
        String key;

        @Option(names = "--json", description = "JSON instead of the bare value")
        boolean json;

        @Override
        public void run() {
            var map = SettingsCodec.toMap(load());
            if (!map.containsKey(key)) {
                ERR.println("no such setting: " + key);
                ERR.println("'proj config list --keys' shows what there is");
                System.exit(4);
                return;
            }
            var value = map.get(key);
            if (json) {
                OUT.println(GSON.toJson(value));
            } else if (value instanceof List<?> list) {
                // One per line, so the output pipes into a loop without anyone parsing brackets.
                list.forEach(OUT::println);
            } else {
                OUT.println(value);
            }
        }
    }

    @Command(name = "set", description = "Set one setting. Several values replace a whole list")
    public static class SetCmd implements Runnable {

        @Parameters(index = "0", description = "Key, e.g. agentCommand or terminal.Tabby.open")
        String key;

        @Parameters(index = "1..*", arity = "1..*", description = "Value. Quote it if it contains spaces")
        List<String> values;

        @Override
        public void run() {
            var settings = load();
            try {
                if (!SettingsCodec.set(settings, key, values)) {
                    ERR.println("no such setting: " + key);
                    ERR.println("'proj config list --keys' shows what there is");
                    System.exit(4);
                    return;
                }
            } catch (IllegalArgumentException e) {
                ERR.println(e.getMessage());
                System.exit(2);
                return;
            }
            save(settings, key + " = " + Toml.value(SettingsCodec.get(settings, key)));
        }
    }

    @Command(name = "unset", aliases = "remove-key",
            description = "Clear a setting, or delete a named terminal, IDE or organisation")
    public static class UnsetCmd implements Runnable {

        @Parameters(arity = "1", description = "Key. terminal.NAME or ide.NAME deletes the whole entry")
        String key;

        @Override
        public void run() {
            var settings = load();
            if (!SettingsCodec.unset(settings, key)) {
                ERR.println("nothing to unset at: " + key);
                System.exit(4);
                return;
            }
            save(settings, "unset " + key);
        }
    }

    @Command(name = "add", description = "Append to a list setting, e.g. codebase.auxiliary")
    public static class AddCmd implements Runnable {

        @Parameters(index = "0", description = "List key: codebase.primary, codebase.mirror, codebase.auxiliary")
        String key;

        @Parameters(index = "1..*", arity = "1..*", description = "Values to append")
        List<String> values;

        @Override
        public void run() {
            var settings = load();
            if (!SettingsCodec.addTo(settings, key, values)) {
                ERR.println(key + " is not a list setting. Use 'proj config set' for a single value, "
                        + "or one of codebase.primary / codebase.mirror / codebase.auxiliary");
                System.exit(4);
                return;
            }
            save(settings, key + " = " + Toml.value(SettingsCodec.get(settings, key)));
        }
    }

    @Command(name = "drop", description = "Remove a value from a list setting")
    public static class DropCmd implements Runnable {

        @Parameters(index = "0", description = "List key, e.g. codebase.auxiliary")
        String key;

        @Parameters(index = "1..*", arity = "1..*", description = "Values to remove")
        List<String> values;

        @Override
        public void run() {
            var settings = load();
            if (!SettingsCodec.removeFrom(settings, key, values)) {
                ERR.println("nothing removed from " + key);
                System.exit(4);
                return;
            }
            save(settings, key + " = " + Toml.value(SettingsCodec.get(settings, key)));
        }
    }

    @Command(name = "path", description = "Print the settings file path")
    public static class PathCmd implements Runnable {
        @Override
        public void run() {
            OUT.println(new SettingsManager().getSettingsFilePath());
        }
    }

    @Command(name = "edit", description = "Open the settings file in the configured editor")
    public static class EditCmd implements Runnable {
        @Override
        public void run() {
            var path = new SettingsManager().getSettingsFilePath();
            var settings = new SettingsManager().loadSettings();
            littlejlib.proj.ui.PrpManagerActions.openInEditor(path, settings);
            OUT.println(path.toString());
        }
    }

    /**
     * Parses the file and reports what it found, without changing anything.
     * <p>
     * Worth having as its own verb because the parser refuses a malformed file rather than
     * half-reading it: after a hand edit this is how you find out whether the next command will
     * work, without the next command being the one that tells you.
     */
    @Command(name = "validate", aliases = "check", description = "Parse the settings file and report")
    public static class ValidateCmd implements Runnable {

        @Option(names = "--json", description = "JSON instead of plain text")
        boolean json;

        @Override
        public void run() {
            var path = new SettingsManager().getSettingsFilePath();
            var report = new LinkedHashMap<String, Object>();
            report.put("file", path.toString());

            if (!Files.exists(path)) {
                report.put("ok", false);
                report.put("error", "no settings file");
                emit(report);
                System.exit(4);
                return;
            }

            try {
                var settings = SettingsCodec.read(Files.readString(path));
                var problems = new ArrayList<String>();
                if (settings.getCodeRepoBases().isEmpty()) problems.add("no code base configured");
                settings.getCodeRepoBases().stream()
                        .filter(b -> b.toPath() == null || !Files.isDirectory(b.toPath()))
                        .forEach(b -> problems.add("code base does not exist: " + b.getPath()));
                if (settings.getOrganizations().isEmpty()) problems.add("no organisations configured");

                report.put("ok", problems.isEmpty());
                report.put("settings", SettingsCodec.toMap(settings).size());
                report.put("codeBases", settings.getCodeRepoBases().size());
                report.put("terminals", settings.getTerminalLaunchers().size());
                report.put("ides", settings.getIdeLaunchers().size());
                report.put("organizations", settings.getOrganizations().size());
                report.put("problems", problems);
                emit(report);
                if (!problems.isEmpty()) System.exit(1);
            } catch (Exception e) {
                report.put("ok", false);
                report.put("error", e.getMessage());
                emit(report);
                System.exit(2);
            }
        }

        void emit(LinkedHashMap<String, Object> report) {
            if (json) {
                OUT.println(GSON.toJson(report));
                return;
            }
            report.forEach((k, v) -> {
                if (v instanceof List<?> list) {
                    if (list.isEmpty()) return;
                    OUT.println(k + ":");
                    list.forEach(p -> OUT.println("   " + p));
                } else {
                    OUT.println(k + "  " + v);
                }
            });
        }
    }
}
