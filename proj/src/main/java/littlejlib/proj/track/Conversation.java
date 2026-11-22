package littlejlib.proj.track;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The conversation inside a claude-code transcript, and nothing else: what the human typed (including
 * prompts typed while Claude was working, stored as {@code queued_command} attachments), Claude's text
 * replies, and the compaction summary if the session was compacted. Tool calls, tool output, thinking,
 * system reminders and command echoes are left out.
 */
public final class Conversation {

    final List<Turn> turns = new ArrayList<>();
    String summary;
    String sessionId;
    String first, last;

    public List<Turn> turns() {
        return turns;
    }

    public String summary() {
        return summary;
    }

    public String sessionId() {
        return sessionId;
    }

    public String first() {
        return first;
    }

    public String last() {
        return last;
    }

    public static Conversation read(Path jsonl) throws IOException {
        var c = new Conversation();
        try (var lines = Files.lines(jsonl, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (!line.isBlank()) c.accept(line);
            });
        }
        return c;
    }

    void accept(String line) {
        JsonObject o;
        try {
            o = JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        if (bool(o, "isSidechain")) return;
        var at = str(o, "timestamp");
        if (at != null) {
            if (first == null) first = at;
            last = at;
        }
        if (sessionId == null) sessionId = str(o, "sessionId");
        switch (String.valueOf(str(o, "type"))) {
            case "user" -> user(o, at);
            case "attachment" -> queued(o, at);
            case "assistant" -> assistant(o, at);
            default -> { }
        }
    }

    void user(JsonObject o, String at) {
        if (bool(o, "isMeta")) return;
        var text = Text.of(o.has("message") ? o.getAsJsonObject("message").get("content") : null, true);
        if (bool(o, "isCompactSummary")) {
            summary = text;
            return;
        }
        human(at, Text.human(text));
    }

    void queued(JsonObject o, String at) {
        var a = o.get("attachment");
        if (a == null || !a.isJsonObject() || !"queued_command".equals(str(a.getAsJsonObject(), "type"))) return;
        human(at, Text.human(Text.of(a.getAsJsonObject().get("prompt"), false)));
    }

    void assistant(JsonObject o, String at) {
        if (!o.has("message")) return;
        var content = o.getAsJsonObject("message").get("content");
        if (content == null || !content.isJsonArray()) return;
        for (var block : content.getAsJsonArray()) {
            if (!block.isJsonObject() || !"text".equals(str(block.getAsJsonObject(), "type"))) continue;
            var text = str(block.getAsJsonObject(), "text");
            if (text == null || text.isBlank()) continue;
            if (turns.isEmpty()) turns.add(new Turn(at, null));
            turns.get(turns.size() - 1).replies.add(text.strip());
        }
    }

    /** A draft re-emitted as it grew, or the same prompt written again, is not a new turn. */
    void human(String at, String text) {
        if (text == null || text.isBlank()) return;
        for (var i = turns.size() - 1; i >= Math.max(0, turns.size() - 3); i--) {
            var t = turns.get(i);
            if (t.human == null) continue;
            if (t.human.equals(text)) return;
            if (i == turns.size() - 1 && t.replies.isEmpty() && text.startsWith(t.human)) {
                t.human = text;
                return;
            }
        }
        turns.add(new Turn(at, text));
    }

    static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    static boolean bool(JsonObject o, String key) {
        var e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }
}
