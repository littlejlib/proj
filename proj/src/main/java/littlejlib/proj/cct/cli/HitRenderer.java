package littlejlib.proj.cct.cli;

import module java.base;

import littlejlib.proj.cct.action.ResumeCommands;
import littlejlib.proj.cct.search.*;
import littlejlib.proj.cct.store.Timestamps;

import static littlejlib.proj.cct.Num.*;

public final class HitRenderer {

    private HitRenderer() {}

    public static void render(List<SearchHit> hits, boolean withResume) {
        if (hits.isEmpty()) { Out.line("no sessions matched"); return; }
        var i = 0;
        for (var h : hits) {
            i++;
            var s = h.session;
            Out.line("%d. %s", i, title(s));
            Out.line("   %s", s.cwd() == null ? "(no cwd recorded)" : s.cwd());
            Out.line("   %s", facts(h));
            if (!h.snippet.isEmpty()) Out.line("   %s", Out.oneLine(h.snippet, 200));
            if (withResume) Out.line("   %s", ResumeCommands.forThisPlatform(s));
            Out.blank();
        }
    }

    private static String title(littlejlib.proj.cct.model.SessionDoc s) {
        var t = s.aiTitle();
        if (t == null || t.isBlank()) t = Out.oneLine(s.firstHumanPrompt(), 70);
        if (t == null || t.isBlank()) t = "(untitled session)";
        return t;
    }

    private static String facts(SearchHit h) {
        var s = h.session;
        var parts = new ArrayList<String>();
        parts.add(Out.shortId(s.sessionId()));
        parts.add(Timestamps.local(n(s.lastActivity())) + " (" + Timestamps.ago(n(s.lastActivity())) + ")");
        if (n(s.transcriptMissing())) {
            parts.add("prompts only, transcript pruned");
            parts.add(n(s.humanTurns()) + " prompts");
        } else {
            parts.add(count(n(s.recordCount())) + " rec");
            parts.add(bytes(n(s.fileBytes())));
            parts.add(n(s.humanTurns()) + " human turns");
        }
        var tok = Filters.totalTokens(s);
        if (tok > 0) parts.add(count(tok) + " tok");
        if (n(s.subagent())) parts.add("subagent");
        var m = matches(h);
        if (!m.isEmpty()) parts.add("matched " + m);
        return String.join("  ·  ", parts);
    }

    private static String matches(SearchHit h) {
        var parts = new ArrayList<String>();
        if (h.cwdMatch) parts.add("path");
        if (h.titleMatch) parts.add("title");
        if (h.promptMatch) parts.add("prompt");
        if (h.humanMatches > 0) parts.add(h.humanMatches + " human");
        if (h.assistantMatches > 0) parts.add(h.assistantMatches + " assistant");
        if (h.thinkingMatches > 0) parts.add(h.thinkingMatches + " thinking");
        return String.join(", ", parts);
    }
}
