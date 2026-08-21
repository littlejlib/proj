package littlejlib.proj.cct.store;

import module java.base;

import com.fasterxml.jackson.core.*;

import static littlejlib.proj.cct.store.RecordReader.*;

public final class MessageReader {

    private MessageReader() {}

    static final int TEXT_CAP = 1 << 16;

    static void read(JsonParser p, RawRecord r) throws IOException {
        if (p.getCurrentToken() != JsonToken.START_OBJECT) { p.skipChildren(); return; }
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            var f = p.currentName();
            p.nextToken();
            switch (f) {
                case "role" -> r.role(str(p));
                case "model" -> r.model(str(p));
                case "content" -> content(p, r);
                case "usage" -> usage(p, r);
                default -> p.skipChildren();
            }
        }
    }

    private static void content(JsonParser p, RawRecord r) throws IOException {
        var tok = p.getCurrentToken();
        if (tok == JsonToken.VALUE_STRING) { r.text(p.getText()); return; }
        if (tok != JsonToken.START_ARRAY) { p.skipChildren(); return; }
        var texts = new ArrayList<String>();
        var thoughts = new ArrayList<String>();
        while (p.nextToken() == JsonToken.START_OBJECT) block(p, r, texts, thoughts);
        if (!texts.isEmpty()) r.text(join(texts));
        if (!thoughts.isEmpty()) r.thinking(join(thoughts));
    }

    private static void block(JsonParser p, RawRecord r,
                              List<String> texts, List<String> thoughts) throws IOException {
        var kind = (String) null;
        var text = (String) null;
        var thinking = (String) null;
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            var f = p.currentName();
            p.nextToken();
            switch (f) {
                case "type" -> kind = str(p);
                case "text" -> text = str(p);
                case "thinking" -> thinking = str(p);
                default -> p.skipChildren();
            }
        }
        if (kind == null) return;
        switch (kind) {
            case "text" -> { if (text != null) texts.add(text); }
            case "thinking" -> { if (thinking != null) thoughts.add(thinking); }
            case "tool_result" -> r.toolResultBlocks(r.toolResultBlocks() + 1);
            case "tool_use" -> r.toolUse(true);
            case "image" -> r.image(true);
            default -> { }
        }
    }

    private static String join(List<String> parts) {
        var s = parts.size() == 1 ? parts.get(0) : String.join("\n\n", parts);
        return s.length() <= TEXT_CAP ? s : s.substring(0, TEXT_CAP);
    }

    private static void usage(JsonParser p, RawRecord r) throws IOException {
        if (p.getCurrentToken() != JsonToken.START_OBJECT) { p.skipChildren(); return; }
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            var f = p.currentName();
            p.nextToken();
            switch (f) {
                case "input_tokens" -> r.inputTokens(r.inputTokens() + longOf(p));
                case "output_tokens" -> r.outputTokens(r.outputTokens() + longOf(p));
                case "cache_read_input_tokens" -> r.cacheReadTokens(r.cacheReadTokens() + longOf(p));
                case "cache_creation_input_tokens" -> r.cacheCreateTokens(r.cacheCreateTokens() + longOf(p));
                default -> p.skipChildren();
            }
        }
    }
}
