package littlejlib.proj.cct.export;

import module java.base;

import com.fasterxml.jackson.core.*;

/**
 * A tool call's arguments, flattened to text.
 * <p>
 * Rendered the way claude-code's own export does: the argument that identifies the call comes first
 * and unabbreviated - the shell command, the file path, the pattern - because that is the part
 * somebody reading the log a week later is looking for. The rest follows as {@code key: value}. The
 * order matters more than it looks: {@code command} before {@code description} is the difference
 * between a log you can search for a command and one you cannot.
 */
public final class ToolInput {

    private ToolInput() {}

    static final List<String> LEAD = List.of(
        "command", "file_path", "path", "pattern", "notebook_path", "url", "query", "prompt");

    static String read(JsonParser p) throws IOException {
        if (p.getCurrentToken() != JsonToken.START_OBJECT) { p.skipChildren(); return null; }
        var fields = new LinkedHashMap<String, String>();
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            var f = p.currentName();
            p.nextToken();
            fields.put(f, value(p));
        }
        return format(fields);
    }

    static String value(JsonParser p) throws IOException {
        return switch (p.getCurrentToken()) {
            case VALUE_STRING -> TranscriptReader.capped(p);
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> p.getText();
            case VALUE_TRUE -> "true";
            case VALUE_FALSE -> "false";
            case VALUE_NULL -> "null";
            case START_ARRAY, START_OBJECT -> { p.skipChildren(); yield "(…)"; }
            default -> { p.skipChildren(); yield null; }
        };
    }

    static String format(Map<String, String> fields) {
        if (fields.isEmpty()) return null;
        var out = new ArrayList<String>();
        for (var key : LEAD) {
            var v = fields.remove(key);
            if (v != null && !v.isBlank()) out.add(v);
        }
        for (var e : fields.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            out.add(e.getKey() + ": " + e.getValue());
        }
        return out.isEmpty() ? null : String.join("\n", out);
    }

    /** The one line that names the call: {@code Bash(cd /c/user && …)}. */
    public static String brief(String tool, String input) {
        var head = input == null ? "" : input.split("\n", 2)[0].replaceAll("\\s+", " ").strip();
        if (head.length() > 100) head = head.substring(0, 99) + "…";
        return (tool == null ? "Tool" : tool) + "(" + head + ")";
    }
}
