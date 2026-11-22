package littlejlib.proj.cct;

import littlejlib.proj.cct.action.Sessions;
import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.search.SearchService;

/**
 * What the 50 surviving prefix-pairs actually are, before deciding whether a fuzzier rule is worth
 * the risk of throwing away a real prompt. Prints the pair, its parent uuids and the gap between
 * them, which is what tells a re-emission from a person typing a longer version of what they just
 * said.
 */
public final class SmokeDupes {

    public static void main(String[] args) {
        var db = Db.get().db();
        var svc = new SearchService(db);
        var shown = 0;
        for (var s : Sessions.all(db)) {
            if (Boolean.TRUE.equals(s.transcriptMissing())) continue;
            var turns = svc.turns(s.sessionId(), 1);
            for (var i = 1; i < turns.size() && shown < 12; i++) {
                var a = turns.get(i - 1);
                var b = turns.get(i);
                if (a.text() == null || b.text() == null || a.text().isBlank()) continue;
                if (!b.text().startsWith(a.text())) continue;
                shown++;
                System.out.println("--- " + s.sessionId().substring(0, 8)
                    + "  seq " + a.seq() + " -> " + b.seq()
                    + "  gap " + (nz(b.timestamp()) - nz(a.timestamp())) + " ms");
                System.out.println("    parents  " + a.parentUuid() + "  |  " + b.parentUuid());
                System.out.println("    shorter  " + cut(a.text()));
                System.out.println("    longer   " + cut(b.text()));
            }
            if (shown >= 12) break;
        }
        System.out.println("shown " + shown);
    }

    static long nz(Long v) { return v == null ? 0 : v; }

    static String cut(String s) {
        var flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() <= 110 ? flat : flat.substring(0, 110) + "…";
    }
}
