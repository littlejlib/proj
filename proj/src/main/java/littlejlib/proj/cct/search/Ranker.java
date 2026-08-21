package littlejlib.proj.cct.search;

import module java.base;

import static littlejlib.proj.cct.Num.*;

public final class Ranker {

    private Ranker() {}

    private static final long DAY = 86_400_000L;

    public static void rank(List<SearchHit> hits) {
        var now = System.currentTimeMillis();
        for (var h : hits) h.score = score(h, now);
        hits.sort(Comparator.<SearchHit>comparingInt(h -> -h.score)
            .thenComparing(h -> -n(h.session.lastActivity())));
    }

    private static int score(SearchHit h, long now) {
        var s = 0;
        if (h.cwdMatch) s += 120;
        if (h.titleMatch) s += 70;
        if (h.promptMatch) s += 60;
        s += Math.min(240, h.humanMatches * 40);
        s += Math.min(80, h.assistantMatches * 8);
        s += Math.min(30, h.thinkingMatches * 2);
        s += recency(n(h.session.lastActivity()), now);
        return s;
    }

    private static int recency(long lastActivity, long now) {
        if (lastActivity <= 0) return 0;
        var days = (now - lastActivity) / DAY;
        if (days <= 1) return 40;
        if (days <= 7) return 30;
        if (days <= 30) return 20;
        if (days <= 90) return 10;
        return 0;
    }
}
