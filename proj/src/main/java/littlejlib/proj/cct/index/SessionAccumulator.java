package littlejlib.proj.cct.index;

import module java.base;

import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static littlejlib.proj.cct.Num.*;

public final class SessionAccumulator {

    private final SessionDoc doc;
    private final Set<String> models, efforts, permModes;

    public SessionAccumulator(SessionDoc doc) {
        this.doc = doc;
        models = uncsv(doc.models());
        efforts = uncsv(doc.efforts());
        permModes = uncsv(doc.permissionModes());
    }

    public SessionDoc doc() { return doc; }

    public void apply(RawRecord r) {
        if (r.cwd() != null) doc.cwd(r.cwd());
        stamps(r.timestampMillis());
        add(models, r.model());
        add(efforts, r.effort());
        add(permModes, r.permissionMode());
        if (r.gitBranch() != null) doc.gitBranch(r.gitBranch());
        if (r.version() != null) doc.version(r.version());
        if (r.entrypoint() != null) doc.entrypoint(r.entrypoint());
        if (r.sessionKind() != null) doc.sessionKind(r.sessionKind());
        tokens(r);
        if (r.image()) doc.imageLineBytes(n(doc.imageLineBytes()) + r.lineBytes());
        if (r.toolResultBlocks() > 0)
            doc.toolResultBlocks(n(doc.toolResultBlocks()) + r.toolResultBlocks());
        typed(r);
    }

    private void typed(RawRecord r) {
        var t = r.type();
        if (t == null) return;
        switch (t) {
            case "ai-title" -> { if (r.aiTitle() != null) doc.aiTitle(r.aiTitle()); }
            case "last-prompt" -> { if (r.lastPrompt() != null) doc.lastPrompt(r.lastPrompt()); }
            case "agent-name" -> { if (r.agentName() != null && doc.aiTitle() == null) doc.aiTitle(r.agentName()); }
            case "system" -> { if (r.messageCount() > 0) doc.compactions(n(doc.compactions()) + 1); }
            default -> { }
        }
    }

    private void stamps(long ts) {
        if (ts <= 0) return;
        if (n(doc.startedAt()) == 0 || ts < doc.startedAt()) doc.startedAt(ts);
        if (ts > n(doc.lastActivity())) doc.lastActivity(ts);
    }

    private void tokens(RawRecord r) {
        doc.inputTokens(n(doc.inputTokens()) + r.inputTokens());
        doc.outputTokens(n(doc.outputTokens()) + r.outputTokens());
        doc.cacheReadTokens(n(doc.cacheReadTokens()) + r.cacheReadTokens());
        doc.cacheCreateTokens(n(doc.cacheCreateTokens()) + r.cacheCreateTokens());
    }

    public void countTurn(TurnDoc t) {
        switch (Layer.ofDepth(n(t.layer()))) {
            case HUMAN -> {
                doc.humanTurns(n(doc.humanTurns()) + 1);
                if (doc.firstHumanPrompt() == null) doc.firstHumanPrompt(t.text());
            }
            case ASSISTANT -> doc.assistantTurns(n(doc.assistantTurns()) + 1);
            case THINKING -> doc.thinkingTurns(n(doc.thinkingTurns()) + 1);
            case TOOLS -> { }
        }
    }

    public SessionDoc finish(long offset, long size, long mtime, int records) {
        doc.models(csv(models)).efforts(csv(efforts)).permissionModes(csv(permModes));
        return doc.indexedOffset(offset).indexedSize(size).indexedMtime(mtime).recordCount(records);
    }

    private static void add(Set<String> set, String v) { if (v != null && !v.isBlank()) set.add(v); }
}
