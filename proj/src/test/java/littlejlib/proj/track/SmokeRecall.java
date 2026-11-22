package littlejlib.proj.track;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PRP 55 piece 5: the conversation reader, the budgeted recall and the jsonl log copy, on an invented transcript.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.track.SmokeRecall" -Dexec.classpathScope=test
 */
public class SmokeRecall {

    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("smokerecall");
        var t = dir.resolve("aaaaaaaa-1111-2222-3333-444444444444.jsonl");
        Files.write(t, transcript());
        reader(t);
        recall(t);
        logCopy(dir, t);
        System.out.println("ok");
        System.exit(0);
    }

    static List<String> transcript() {
        var s = "\"sessionId\":\"aaaaaaaa-1111-2222-3333-444444444444\"";
        return List.of(
                "{\"type\":\"mode\",\"mode\":\"normal\"," + s + "}",
                "{\"type\":\"user\",\"timestamp\":\"2026-11-21T08:00:00Z\"," + s + ",\"message\":{\"role\":\"user\",\"content\":\"build the csv expo\"}}",
                "{\"type\":\"user\",\"timestamp\":\"2026-11-21T08:00:01Z\"," + s + ",\"message\":{\"role\":\"user\",\"content\":\"build the csv exporter\"}}",
                "{\"type\":\"user\",\"isMeta\":true," + s + ",\"message\":{\"role\":\"user\",\"content\":\"meta text\"}}",
                "{\"type\":\"assistant\",\"timestamp\":\"2026-11-21T08:00:05Z\"," + s + ",\"message\":{\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},"
                        + "{\"type\":\"text\",\"text\":\"Reading the code first.\"},{\"type\":\"tool_use\",\"name\":\"Bash\",\"input\":{}}]}}",
                "{\"type\":\"user\"," + s + ",\"message\":{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"content\":\"lots of output\"}]}}",
                "{\"type\":\"api-request-blob\",\"blob\":\"" + "x".repeat(500) + "\"," + s + "}",
                "{\"type\":\"attachment\",\"timestamp\":\"2026-11-21T08:01:00Z\"," + s + ",\"attachment\":{\"type\":\"queued_command\",\"prompt\":\"also quote commas\"}}",
                "{\"type\":\"attachment\"," + s + ",\"attachment\":{\"type\":\"file\",\"content\":\"a file\"}}",
                "{\"type\":\"assistant\",\"timestamp\":\"2026-11-21T08:02:00Z\"," + s + ",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Quoting added.\"}]}}",
                "{\"type\":\"assistant\"," + s + ",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Done: exporter and quoting.\"}]}}",
                "{\"type\":\"user\"," + s + ",\"message\":{\"role\":\"user\",\"content\":\"<command-name>/clear</command-name>\"}}",
                "{\"type\":\"user\"," + s + ",\"message\":{\"role\":\"user\",\"content\":\"<system-reminder>x</system-reminder>\\nship it\"}}",
                "{\"type\":\"user\",\"isCompactSummary\":true," + s + ",\"message\":{\"role\":\"user\",\"content\":\"Summary: an exporter was built.\"}}",
                "{\"type\":\"user\",\"isSidechain\":true," + s + ",\"message\":{\"role\":\"user\",\"content\":\"a subagent prompt\"}}",
                "not json at all");
    }

    static void reader(Path t) throws Exception {
        var c = Conversation.read(t);
        var humans = c.turns().stream().map(Turn::human).toList();
        check(humans.equals(List.of("build the csv exporter", "also quote commas", "ship it")), "human turns: " + humans);
        check(c.turns().get(0).replies().equals(List.of("Reading the code first.")), "text only, no tool_use: " + c.turns().get(0).replies());
        check(c.turns().get(1).replies().size() == 2, "replies grouped under the queued turn");
        check("Summary: an exporter was built.".equals(c.summary()) && c.sessionId().startsWith("aaaaaaaa"), "summary and session");
        System.out.println("  pass  reader: drafts merged, queued prompts kept, tool/meta/command/sidechain left out");
    }

    static void recall(Path t) throws Exception {
        var c = Conversation.read(t);
        var all = String.join("\n", Recall.render(c, 10_000));
        check(all.contains("build the csv exporter") && all.contains("Quoting added.") && all.contains("compaction summary"), all);
        check(!all.contains("lots of output") && !all.contains("tool_use"), "no tool output");
        var tight = String.join("\n", Recall.render(c, 18));
        check(tight.contains("ship it") && !tight.contains("build the csv exporter") && tight.contains("earlier turns left out"), "tight: " + tight);
        var mid = String.join("\n", Recall.render(c, 35));
        check(mid.contains("Done: exporter and quoting.") && mid.contains("1 earlier replies to this turn left out"), "last reply kept first: " + mid);
        System.out.println("  pass  recall: whole when it fits, newest turns and last replies first when not");
    }

    static void logCopy(Path dir, Path t) throws Exception {
        var prp = Files.createDirectories(dir.resolve("billing").resolve("prp"));
        Files.writeString(prp.resolve("07-prp.99.03.logs.md"), "old log");
        var w = LogCopy.write(prp, "07", t);
        check(w.file().getFileName().toString().equals("07-prp.99.04.logs.jsonl") && !w.replaced(), "serial continues after old logs: " + w.file());
        var text = Files.readString(w.file());
        check(!text.contains("api-request-blob") && !text.contains("\"mode\"") && !text.contains("a file") && text.contains("queued_command"), "noise left out");
        check(w.kept() == 12 && w.dropped() == 4, "kept " + w.kept() + " dropped " + w.dropped());
        var again = LogCopy.write(prp, "07", t);
        check(again.replaced() && again.file().equals(w.file()), "same session overwrites its own file");
        check(Conversation.read(w.file()).turns().size() == 3, "the copy reads back as the same conversation");
        System.out.println("  pass  log copy: noise dropped, serial continues, one file per session");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
