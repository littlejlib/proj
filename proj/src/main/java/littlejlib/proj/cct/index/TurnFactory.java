package littlejlib.proj.cct.index;

import module java.base;

import littlejlib.proj.cct.model.TurnDoc;
import littlejlib.proj.cct.store.*;

public final class TurnFactory {

    private TurnFactory() {}

    public static List<TurnDoc> of(RawRecord r, String sessionId, int seq, IndexOptions opts) {
        var human = humanText(r);
        if (human != null) return List.of(turn(r, sessionId, seq, Layer.HUMAN, human));

        if (!"assistant".equals(r.type())) return List.of();

        var out = new ArrayList<TurnDoc>(2);
        if (opts.includeAssistant() && notBlank(r.text()))
            out.add(turn(r, sessionId, seq, Layer.ASSISTANT, r.text()));
        if (opts.includeThinking() && notBlank(r.thinking()))
            out.add(turn(r, sessionId, seq, Layer.THINKING, r.thinking()));
        return out;
    }

    static String humanText(RawRecord r) {
        var t = r.type();
        if (t == null) return null;
        if ("user".equals(t))
            return "typed".equals(r.promptSource()) && MachineTag.isHuman(r.text()) ? r.text() : null;
        if ("queue-operation".equals(t))
            return "enqueue".equals(r.operation()) && MachineTag.isHuman(r.content()) ? r.content() : null;
        return null;
    }

    private static TurnDoc turn(RawRecord r, String sessionId, int seq, Layer layer, String text) {
        return new TurnDoc()
            .turnKey(sessionId + "#" + seq + ":" + layer.depth)
            .sessionId(sessionId)
            .uuid(r.uuid())
            .parentUuid(r.parentUuid())
            .seq(seq)
            .layer(layer.depth)
            .role(layer.label)
            .text(text)
            .timestamp(r.timestampMillis())
            .sidechain(r.sidechain())
            .agentId(r.agentId())
            .model(r.model())
            .effort(r.effort());
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
