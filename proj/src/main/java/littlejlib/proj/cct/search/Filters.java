package littlejlib.proj.cct.search;

import module java.base;

import littlejlib.proj.cct.model.SessionDoc;

import static littlejlib.proj.cct.Num.*;

public final class Filters {

    private Filters() {}

    public static boolean passes(SessionDoc s, SearchQuery q) {
        if (s == null) return false;
        if (set(q.projectEncoded()) && !q.projectEncoded().equals(s.projectEncoded())) return false;
        if (set(q.cwdContains()) && !containsIgnoreCase(s.cwd(), q.cwdContains())) return false;
        if (q.fromMillis() != null && n(s.lastActivity()) < q.fromMillis()) return false;
        if (q.toMillis() != null && n(s.lastActivity()) > q.toMillis()) return false;
        if (set(q.model()) && !csvHas(s.models(), q.model())) return false;
        if (set(q.effort()) && !csvHas(s.efforts(), q.effort())) return false;
        if (set(q.permissionMode()) && !csvHas(s.permissionModes(), q.permissionMode())) return false;
        if (set(q.gitBranch()) && !q.gitBranch().equalsIgnoreCase(s.gitBranch())) return false;
        if (set(q.sessionKind()) && !q.sessionKind().equalsIgnoreCase(s.sessionKind())) return false;
        if (n(q.subagentsOnly()) && !n(s.subagent())) return false;
        if (q.minFileBytes() != null && n(s.fileBytes()) < q.minFileBytes()) return false;
        if (q.minTotalTokens() != null && totalTokens(s) < q.minTotalTokens()) return false;
        return true;
    }

    public static long totalTokens(SessionDoc s) {
        return n(s.inputTokens()) + n(s.outputTokens())
            + n(s.cacheReadTokens()) + n(s.cacheCreateTokens());
    }

    private static boolean set(String s) { return s != null && !s.isBlank(); }

    private static boolean containsIgnoreCase(String hay, String needle) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean csvHas(String csv, String want) {
        for (var v : uncsv(csv)) if (v.equalsIgnoreCase(want) || v.toLowerCase(Locale.ROOT)
            .contains(want.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
