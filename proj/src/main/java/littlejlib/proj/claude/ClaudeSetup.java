package littlejlib.proj.claude;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * proj's own place in Claude Code, the way jacli has its own: {@code ~/.claude/proj.md} (written from a
 * bundled text, imported by one {@code @proj.md} line in {@code ~/.claude/CLAUDE.md}) and a SessionStart
 * hook in {@code ~/.claude/settings.json} that points a session at {@code proj prp guide} when its folder
 * has a {@code prp/} folder. Nothing else in those files is touched.
 */
public final class ClaudeSetup {

    private ClaudeSetup() { }

    public static final String RESOURCE = "/claude/proj-claude.md", IMPORT = "@proj.md", HOOK_MARK = "proj claude hook session-start";

    static final String HOOK_COMMAND = "d=\"${CLAUDE_PROJECT_DIR:-$PWD}\"; if [ -d \"$d/prp\" ]; then " + HOOK_MARK + "; fi; true";

    static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static Path home = Path.of(System.getProperty("user.home"), ".claude");

    public static Path dir() {
        return home;
    }

    public static String text() throws IOException {
        try (var in = ClaudeSetup.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("missing from this build: " + RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** What is wrong or missing, empty when everything is current. */
    public static List<String> status() throws IOException {
        var out = new ArrayList<String>();
        var md = home.resolve("proj.md");
        if (!Files.isRegularFile(md)) out.add("~/.claude/proj.md is missing");
        else if (!Files.readString(md, StandardCharsets.UTF_8).equals(text())) out.add("~/.claude/proj.md is out of date");
        if (!imported()) out.add("~/.claude/CLAUDE.md does not import @proj.md");
        if (!hooked()) out.add("~/.claude/settings.json has no SessionStart hook for proj");
        return out;
    }

    public static List<String> install() throws IOException {
        var done = new ArrayList<String>();
        Files.createDirectories(home);
        var md = home.resolve("proj.md");
        if (!Files.isRegularFile(md) || !Files.readString(md, StandardCharsets.UTF_8).equals(text())) {
            Files.writeString(md, text(), StandardCharsets.UTF_8);
            done.add("wrote ~/.claude/proj.md");
        }
        if (!imported()) {
            var claude = home.resolve("CLAUDE.md");
            var was = Files.isRegularFile(claude) ? Files.readString(claude, StandardCharsets.UTF_8) : "";
            backup(claude);
            Files.writeString(claude, was + (was.isEmpty() || was.endsWith("\n") ? "" : "\n") + IMPORT + "\n", StandardCharsets.UTF_8);
            done.add("added " + IMPORT + " to ~/.claude/CLAUDE.md");
        }
        if (!hooked()) {
            editSettings(true);
            done.add("added the SessionStart hook to ~/.claude/settings.json");
        }
        return done;
    }

    public static List<String> uninstall() throws IOException {
        var done = new ArrayList<String>();
        if (Files.deleteIfExists(home.resolve("proj.md"))) done.add("removed ~/.claude/proj.md");
        if (imported()) {
            var claude = home.resolve("CLAUDE.md");
            backup(claude);
            var kept = Files.readAllLines(claude, StandardCharsets.UTF_8).stream().filter(l -> !l.strip().equals(IMPORT)).toList();
            Files.writeString(claude, kept.isEmpty() ? "" : String.join("\n", kept) + "\n", StandardCharsets.UTF_8);
            done.add("removed " + IMPORT + " from ~/.claude/CLAUDE.md");
        }
        if (hooked()) {
            editSettings(false);
            done.add("removed the SessionStart hook from ~/.claude/settings.json");
        }
        return done;
    }

    static boolean imported() throws IOException {
        var claude = home.resolve("CLAUDE.md");
        return Files.isRegularFile(claude) && Files.readAllLines(claude, StandardCharsets.UTF_8).stream().anyMatch(l -> l.strip().equals(IMPORT));
    }

    static boolean hooked() throws IOException {
        var s = home.resolve("settings.json");
        return Files.isRegularFile(s) && Files.readString(s, StandardCharsets.UTF_8).contains(HOOK_MARK);
    }

    /** Adds or removes only proj's own SessionStart entry; every other key and hook stays as it was. */
    static void editSettings(boolean add) throws IOException {
        var f = home.resolve("settings.json");
        var root = Files.isRegularFile(f) ? JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        if (!root.has("hooks")) root.add("hooks", new JsonObject());
        var hooks = root.getAsJsonObject("hooks");
        if (!hooks.has("SessionStart")) hooks.add("SessionStart", new JsonArray());
        var start = hooks.getAsJsonArray("SessionStart");
        for (var i = start.size() - 1; i >= 0; i--) if (start.get(i).toString().contains(HOOK_MARK)) start.remove(i);
        if (add) {
            var cmd = new JsonObject();
            cmd.addProperty("type", "command");
            cmd.addProperty("command", HOOK_COMMAND);
            cmd.addProperty("timeout", 15);
            var list = new JsonArray();
            list.add(cmd);
            var entry = new JsonObject();
            entry.add("hooks", list);
            start.add(entry);
        }
        if (start.isEmpty()) hooks.remove("SessionStart");
        backup(f);
        var tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.writeString(tmp, PRETTY.toJson(root) + "\n", StandardCharsets.UTF_8);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
    }

    static void backup(Path f) throws IOException {
        if (!Files.isRegularFile(f)) return;
        var stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Files.copy(f, f.resolveSibling(f.getFileName() + ".before-proj-" + stamp), StandardCopyOption.REPLACE_EXISTING);
    }
}
