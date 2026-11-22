package littlejlib.proj.claude;

import com.google.gson.JsonParser;

import java.nio.file.Files;

/**
 * proj's place in Claude Code, on a fake ~/.claude in a temp folder: install adds only proj's pieces,
 * a second install changes nothing, undo leaves everything else as it was.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.claude.SmokeClaudeSetup" -Dexec.classpathScope=test
 */
public class SmokeClaudeSetup {

    public static void main(String[] args) throws Exception {
        var home = Files.createTempDirectory("smokeclaude");
        ClaudeSetup.home = home;
        var claudeMd = "@other/prompt.md\n@tool.md\n";
        var settings = "{\n  \"model\": \"x\",\n  \"hooks\": {\n    \"PreCompact\": [ { \"matcher\": \"auto\", \"hooks\": [ { \"type\": \"command\", \"command\": \"keep me\" } ] } ]\n  }\n}\n";
        Files.writeString(home.resolve("CLAUDE.md"), claudeMd);
        Files.writeString(home.resolve("settings.json"), settings);

        check(ClaudeSetup.status().size() == 3, "three things missing before install");
        check(ClaudeSetup.install().size() == 3, "three things done");
        check(ClaudeSetup.status().isEmpty(), "current after install: " + ClaudeSetup.status());
        check(ClaudeSetup.install().isEmpty(), "second install changes nothing");
        var md = Files.readString(home.resolve("CLAUDE.md"));
        check(md.equals(claudeMd + "@proj.md\n"), "one import line added: " + md);
        var s = JsonParser.parseString(Files.readString(home.resolve("settings.json"))).getAsJsonObject();
        check(s.get("model").getAsString().equals("x"), "other keys kept");
        check(s.getAsJsonObject("hooks").get("PreCompact").toString().contains("keep me"), "other hooks kept");
        var start = s.getAsJsonObject("hooks").getAsJsonArray("SessionStart");
        check(start.size() == 1 && start.toString().contains(ClaudeSetup.HOOK_MARK), "one SessionStart entry");
        check(Files.readString(home.resolve("proj.md")).contains("proj prp guide"), "proj.md written");

        Files.writeString(home.resolve("proj.md"), "old text");
        check(ClaudeSetup.status().equals(java.util.List.of("~/.claude/proj.md is out of date")), "stale text noticed");
        check(ClaudeSetup.install().size() == 1, "only the text refreshed");

        check(ClaudeSetup.uninstall().size() == 3, "three things removed");
        check(Files.readString(home.resolve("CLAUDE.md")).equals(claudeMd), "CLAUDE.md as before");
        var after = JsonParser.parseString(Files.readString(home.resolve("settings.json"))).getAsJsonObject();
        check(!after.getAsJsonObject("hooks").has("SessionStart") && after.getAsJsonObject("hooks").has("PreCompact"), "only proj's hook removed");
        check(!Files.exists(home.resolve("proj.md")), "proj.md removed");
        try (var files = Files.list(home)) {
            check(files.anyMatch(f -> f.getFileName().toString().startsWith("settings.json.before-proj-")), "a backup of settings.json kept");
        }
        System.out.println("  pass  claude setup: install, idempotent, refresh, undo, nothing else touched");
        System.out.println("ok");
        System.exit(0);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
