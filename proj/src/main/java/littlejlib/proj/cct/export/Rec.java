package littlejlib.proj.cct.export;

import module java.base;

import littlejlib.proj.cct.store.MachineTag;
import littlejlib.proj.cct.store.Timestamps;

/**
 * One jsonl record while it is being read, and the rule for turning it into entries.
 * <p>
 * ONE RECORD CAN BE SEVERAL ENTRIES, in order: Claude says something, then calls two tools, all in
 * the same assistant record. Flattening them into one blob would lose the sequence, which is the
 * only thing that makes a log readable - so each block becomes its own entry and the file keeps the
 * order it happened in.
 */
final class Rec {

    String type, promptSource, agentId, effort, model, operation, queued;
    boolean sidechain;
    long timestamp;
    final List<Block> blocks = new ArrayList<>();

    void stamp(String iso) { timestamp = Timestamps.millis(iso); }

    void emit(List<TranscriptEntry> out) {
        if (type == null) return;
        switch (type) {
            case "user" -> user(out);
            case "assistant" -> assistant(out);
            case "queue-operation" -> queued(out);
            default -> { }
        }
    }

    private void user(List<TranscriptEntry> out) {
        // A typed prompt and a tool result are both "user" records. The first is you; the second is
        // the machine answering Claude, and mislabelling it as you would be the worst kind of wrong
        // in a log whose whole value is knowing who said what.
        if ("typed".equals(promptSource)) {
            for (var b : blocks) if (isText(b)) add(out, TranscriptEntry.USER, null, b.text, false);
            return;
        }
        for (var b : blocks) {
            if ("tool_result".equals(b.kind)) add(out, TranscriptEntry.TOOL_RESULT, null, b.text, b.error);
            else if (isText(b)) add(out, TranscriptEntry.TOOL_RESULT, null, b.text, false);
        }
    }

    private void assistant(List<TranscriptEntry> out) {
        for (var b : blocks) {
            if (isText(b)) add(out, TranscriptEntry.ASSISTANT, null, b.text, false);
            else if ("thinking".equals(b.kind) && b.text != null && !b.text.isBlank())
                add(out, TranscriptEntry.THINKING, null, b.text, false);
            else if ("tool_use".equals(b.kind))
                add(out, TranscriptEntry.TOOL_USE, b.name, b.input, false);
        }
    }

    private void queued(List<TranscriptEntry> out) {
        if (!"enqueue".equals(operation) || !MachineTag.isHuman(queued)) return;
        add(out, TranscriptEntry.QUEUED, null, queued, false);
    }

    private static boolean isText(Block b) {
        return "text".equals(b.kind) && b.text != null && !b.text.isBlank();
    }

    private void add(List<TranscriptEntry> out, String kind, String tool, String body, boolean error) {
        out.add(new TranscriptEntry()
            .kind(kind)
            .tool(tool)
            .body(body == null ? "" : body)
            .error(error)
            .timestamp(timestamp)
            .sidechain(sidechain)
            .agentId(agentId)
            .model(model)
            .effort(effort));
    }
}
