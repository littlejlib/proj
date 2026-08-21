package littlejlib.proj.cct.export;

import module java.base;

import com.fasterxml.jackson.core.*;
import littlejlib.proj.cct.store.*;

/**
 * The whole session, in order, tool calls and their output included.
 * <p>
 * A SECOND parser, deliberately, rather than an option on {@link RecordReader}. That one indexes
 * 825 MB in twelve seconds precisely because it never materialises a tool result or a base64 image,
 * and this one has to materialise both - so they are kept apart and the fast path stays fast. This
 * runs over ONE file at a time and never over the store.
 * <p>
 * Base64 is still never read: an {@code image} block is recorded as a marker and its {@code source}
 * is skipped without touching the value. Every string that IS read is capped, because one measured
 * line in this store is 1.35 MB.
 */
public final class TranscriptReader {

    private TranscriptReader() {}

    static final int STRING_CAP = 1 << 20;

    static final JsonFactory FACTORY = JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder()
            .maxStringLength(Integer.MAX_VALUE)
            .maxNestingDepth(2000)
            .build())
        .build();

    public static List<TranscriptEntry> read(Path jsonl) {
        var out = new ArrayList<TranscriptEntry>();
        try (var cur = new LineCursor(jsonl, 0)) {
            while (cur.next()) line(cur.buffer(), cur.length(), out);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + jsonl, e);
        }
        return dedupeUserTurns(out);
    }

    /**
     * The same rule the indexer applies, for the same reason: ONE typed prompt is written to the
     * transcript several times, and a prompt that appears three times in a log is worse here than in
     * a search result - it makes the reader wonder whether the user really said it three times.
     * <p>
     * A user entry whose text is a prefix of a LATER user entry is a draft of it. Everything else,
     * assistant text and tool blocks included, is left exactly where it was: their ORDER is the
     * record, and removing anything from that stream would be removing part of the work.
     */
    static List<TranscriptEntry> dedupeUserTurns(List<TranscriptEntry> all) {
        var later = new ArrayList<String>();
        for (var e : all) if (e.isUser()) later.add(e.body() == null ? "" : e.body().strip());
        var out = new ArrayList<TranscriptEntry>(all.size());
        var seen = 0;
        for (var e : all) {
            if (!e.isUser()) { out.add(e); continue; }
            var mine = later.get(seen);
            seen++;
            var superseded = false;
            for (var j = seen; j < later.size() && !superseded; j++)
                superseded = !mine.isEmpty() && later.get(j).startsWith(mine);
            if (!superseded) out.add(e);
        }
        return out;
    }

    static void line(byte[] buf, int len, List<TranscriptEntry> out) {
        if (len <= 1) return;
        var rec = new Rec();
        try (var p = FACTORY.createParser(buf, 0, len)) {
            if (p.nextToken() != JsonToken.START_OBJECT) return;
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                var f = p.currentName();
                p.nextToken();
                switch (f) {
                    case "type" -> rec.type = str(p);
                    case "timestamp" -> rec.stamp(str(p));
                    case "promptSource" -> rec.promptSource = str(p);
                    case "isSidechain" -> rec.sidechain = p.getCurrentToken() == JsonToken.VALUE_TRUE;
                    case "agentId" -> rec.agentId = str(p);
                    case "effort" -> rec.effort = str(p);
                    case "operation" -> rec.operation = str(p);
                    case "content" -> { if (p.getCurrentToken() == JsonToken.VALUE_STRING) rec.queued = capped(p); else p.skipChildren(); }
                    case "message" -> message(p, rec);
                    default -> p.skipChildren();
                }
            }
        } catch (Exception ignored) {
            // a truncated last line while claude-code is still writing is not a failure
        }
        rec.emit(out);
    }

    static void message(JsonParser p, Rec rec) throws IOException {
        if (p.getCurrentToken() != JsonToken.START_OBJECT) { p.skipChildren(); return; }
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            var f = p.currentName();
            p.nextToken();
            switch (f) {
                case "model" -> rec.model = str(p);
                case "content" -> content(p, rec);
                default -> p.skipChildren();
            }
        }
    }

    static void content(JsonParser p, Rec rec) throws IOException {
        var tok = p.getCurrentToken();
        if (tok == JsonToken.VALUE_STRING) { rec.blocks.add(Block.text(capped(p))); return; }
        if (tok != JsonToken.START_ARRAY) { p.skipChildren(); return; }
        while (p.nextToken() == JsonToken.START_OBJECT) rec.blocks.add(block(p));
    }

    static Block block(JsonParser p) throws IOException {
        var b = new Block();
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            var f = p.currentName();
            p.nextToken();
            switch (f) {
                case "type" -> b.kind = str(p);
                case "text" -> b.text = capped(p);
                case "thinking" -> b.text = capped(p);
                case "name" -> b.name = str(p);
                case "is_error" -> b.error = p.getCurrentToken() == JsonToken.VALUE_TRUE;
                case "input" -> b.input = ToolInput.read(p);
                case "content" -> b.text = resultText(p);
                default -> p.skipChildren();
            }
        }
        return b;
    }

    /** A tool result is a string, or an array of blocks of which only the text ones are wanted. */
    static String resultText(JsonParser p) throws IOException {
        var tok = p.getCurrentToken();
        if (tok == JsonToken.VALUE_STRING) return capped(p);
        if (tok != JsonToken.START_ARRAY) { p.skipChildren(); return null; }
        var parts = new ArrayList<String>();
        while (p.nextToken() == JsonToken.START_OBJECT) {
            var kind = (String) null;
            var text = (String) null;
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                var f = p.currentName();
                p.nextToken();
                switch (f) {
                    case "type" -> kind = str(p);
                    case "text" -> text = capped(p);
                    // source holds the base64. Skipped without reading the value, always.
                    default -> p.skipChildren();
                }
            }
            if ("image".equals(kind)) parts.add("[image]");
            else if (text != null) parts.add(text);
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    static String str(JsonParser p) throws IOException {
        return p.getCurrentToken() == JsonToken.VALUE_STRING ? p.getText() : null;
    }

    static String capped(JsonParser p) throws IOException {
        if (p.getCurrentToken() != JsonToken.VALUE_STRING) return null;
        var s = p.getText();
        return s.length() <= STRING_CAP ? s : s.substring(0, STRING_CAP) + "\n[… value truncated]";
    }
}
