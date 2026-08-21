package littlejlib.proj.cct.index;

import module java.base;

import com.arcadedb.database.Database;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static xyz.jphil.arcadedb.datahelper.Query.query;
import static littlejlib.proj.cct.Num.*;
import static littlejlib.proj.cct.model.SessionDoc_A.$sessionId;

public final class HistorySessions {

    private HistorySessions() {}

    public static int rebuild(Database db, int batchSize) {
        clearPrevious(db);
        var live = transcriptIds(db);
        var grouped = new LinkedHashMap<String, List<HistoryDoc>>();
        for (var h : query(db, HistoryDoc.TYPEDEF).orderByAsc(HistoryDoc_A.$timestamp)) {
            var id = h.sessionId();
            if (id == null || id.isBlank() || live.contains(id)) continue;
            grouped.computeIfAbsent(id, k -> new ArrayList<>()).add(h);
        }
        var made = 0;
        for (var e : grouped.entrySet()) {
            if (!worthKeeping(e.getValue())) continue;
            write(db, e.getKey(), e.getValue());
            made++;
        }
        return made;
    }

    private static boolean worthKeeping(List<HistoryDoc> prompts) {
        for (var p : prompts) if (substantive(p.display())) return true;
        return false;
    }

    private static String firstSubstantive(List<HistoryDoc> prompts, String fallback) {
        for (var p : prompts) if (substantive(p.display())) return p.display();
        return fallback;
    }

    private static boolean substantive(String display) {
        if (display == null) return false;
        var s = display.strip();
        if (s.length() < 3) return false;
        return s.charAt(0) != '/';
    }

    private static Set<String> transcriptIds(Database db) {
        var out = new HashSet<String>();
        for (var s : query(db, SessionDoc.TYPEDEF)) if (!n(s.transcriptMissing())) out.add(s.sessionId());
        return out;
    }

    private static void clearPrevious(Database db) {
        var gone = new ArrayList<String>();
        for (var s : query(db, SessionDoc.TYPEDEF)) if (n(s.transcriptMissing())) gone.add(s.sessionId());
        if (gone.isEmpty()) return;
        db.transaction(() -> {
            for (var id : gone) {
                db.command("sql", "DELETE FROM " + TurnDoc.__ + " WHERE sessionId = ?", id);
                db.command("sql", "DELETE FROM " + SessionDoc.__ + " WHERE sessionId = ?", id);
            }
        });
    }

    private static void write(Database db, String sessionId, List<HistoryDoc> prompts) {
        var first = prompts.get(0);
        var last = prompts.get(prompts.size() - 1);
        var cwd = pickCwd(prompts);
        var doc = new SessionDoc()
            .sessionId(sessionId)
            .cwd(cwd)
            .projectEncoded(PathKey.encode(cwd))
            .transcriptMissing(true)
            .subagent(false)
            .firstHumanPrompt(firstSubstantive(prompts, first.display()))
            .lastPrompt(last.display())
            .startedAt(n(first.timestamp()))
            .lastActivity(n(last.timestamp()))
            .humanTurns(prompts.size())
            .recordCount(prompts.size())
            .fileBytes(0L);
        db.transaction(() -> {
            doc.in(db).whereEq($sessionId, sessionId).upsert();
            var seq = 0;
            for (var p : prompts) {
                new TurnDoc()
                    .turnKey(sessionId + "#h" + seq + ":1")
                    .sessionId(sessionId)
                    .seq(seq)
                    .layer(1)
                    .role("human")
                    .text(p.display())
                    .timestamp(n(p.timestamp()))
                    .sidechain(false)
                    .in(db).insert();
                seq++;
            }
        });
    }

    private static String pickCwd(List<HistoryDoc> prompts) {
        for (var i = prompts.size() - 1; i >= 0; i--) {
            var p = prompts.get(i).project();
            if (p != null && !p.isBlank()) return p;
        }
        return null;
    }
}
