package littlejlib.proj.cct.ui;

import module java.base;

import littlejlib.proj.cct.export.*;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.Layer;

/**
 * The viewer's widest scope, read from the session FILE rather than from the index.
 * <p>
 * Tool calls and their output are layer 4, which the indexer deliberately never stores - that is
 * where nearly all of the store's 825 MB lives, and indexing it would trade a twelve-second index for
 * a several-minute one in order to make searchable the least interesting text on the machine. But
 * READING one session's worth is cheap, so when the scope is widened to include them they are
 * streamed off disk with the same reader the export uses. The window and the exported log therefore
 * agree about what happened, which they would not if this had its own parser.
 * <p>
 * They arrive as ordinary {@code TurnDoc}s, so the existing cell, copy, find and export actions all
 * work on them unchanged. Only the layer distinguishes them.
 */
final class FileTurns {

    private FileTurns() {}

    static final int RESULT_LINES = 40;

    static List<TurnDoc> of(SessionDoc s) {
        var file = Path.of(s.filePath());
        if (!Files.isRegularFile(file)) return List.of();
        var out = new ArrayList<TurnDoc>();
        var seq = 0;
        for (var e : TranscriptReader.read(file)) {
            var turn = convert(e, s.sessionId(), seq++);
            if (turn != null) out.add(turn);
        }
        return out;
    }

    static TurnDoc convert(TranscriptEntry e, String sessionId, int seq) {
        var layer = switch (e.kind()) {
            case TranscriptEntry.USER, TranscriptEntry.QUEUED -> Layer.HUMAN;
            case TranscriptEntry.ASSISTANT -> Layer.ASSISTANT;
            case TranscriptEntry.TOOL_USE, TranscriptEntry.TOOL_RESULT -> Layer.TOOLS;
            default -> null;
        };
        if (layer == null) return null;
        return new TurnDoc()
            .turnKey(sessionId + "#f" + seq)
            .sessionId(sessionId)
            .seq(seq)
            .layer(layer.depth)
            .role(layer == Layer.TOOLS ? toolRole(e) : layer.label)
            .text(text(e))
            .timestamp(e.timestamp())
            .sidechain(e.sidechain())
            .agentId(e.agentId())
            .model(e.model())
            .effort(e.effort());
    }

    static String toolRole(TranscriptEntry e) {
        return TranscriptEntry.TOOL_USE.equals(e.kind())
            ? (e.tool() == null ? "tool" : e.tool()) : "result";
    }

    static String text(TranscriptEntry e) {
        var body = e.body() == null ? "" : e.body();
        if (TranscriptEntry.TOOL_USE.equals(e.kind()))
            return Marks.AGENT + " " + ToolInput.brief(e.tool(), body) + "\n" + body.strip();
        if (TranscriptEntry.TOOL_RESULT.equals(e.kind()))
            return Marks.RESULT + (e.error() ? "  ERROR" : "") + "\n" + clip(body.strip());
        return body;
    }

    /** A 1.35 MB tool result would freeze the list. The export keeps all of it; the viewer does not. */
    static String clip(String body) {
        var lines = body.split("\n", -1);
        if (lines.length <= RESULT_LINES) return body;
        var out = new StringBuilder();
        for (var i = 0; i < RESULT_LINES; i++) out.append(lines[i]).append('\n');
        return out.append(Marks.ELIDED).append(" +").append(lines.length - RESULT_LINES)
            .append(" lines - Alt+E exports the whole thing").toString();
    }
}
