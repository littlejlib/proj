package littlejlib.proj.cct.index;

import module java.base;

import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static littlejlib.proj.cct.Num.*;

/** Sessions whose transcript was pruned, rebuilt from the typed-prompt history alone. */
public final class HistorySessions {

    private HistorySessions() {}

    public static int rebuild(Db db, Map<String, SessionDoc> sessions) {
        clearPrevious(db, sessions);
        var live = new HashSet<>(sessions.keySet());
        var grouped = new LinkedHashMap<String, List<HistoryDoc>>();
        var history = new ArrayList<>(db.history().all());
        history.sort(Comparator.comparingLong(h -> n(h.timestamp())));
        for (var h : history) {
            var id = h.sessionId();
            if (id == null || id.isBlank() || live.contains(id)) continue;
            grouped.computeIfAbsent(id, k -> new ArrayList<>()).add(h);
        }
        var made = 0;
        for (var e : grouped.entrySet()) {
            if (!worthKeeping(e.getValue())) continue;
            write(db, sessions, e.getKey(), e.getValue());
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

    private static void clearPrevious(Db db, Map<String, SessionDoc> sessions) {
        var gone = new ArrayList<String>();
        for (var s : sessions.values()) if (n(s.transcriptMissing())) gone.add(s.sessionId());
        for (var id : gone) {
            db.turns().drop(id);
            sessions.remove(id);
        }
    }

    private static void write(Db db, Map<String, SessionDoc> sessions, String sessionId, List<HistoryDoc> prompts) {
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
        var turns = new ArrayList<TurnDoc>();
        var seq = 0;
        for (var p : prompts) {
            turns.add(new TurnDoc()
                .turnKey(sessionId + "#h" + seq + ":1")
                .sessionId(sessionId)
                .seq(seq)
                .layer(1)
                .role("human")
                .text(p.display())
                .timestamp(n(p.timestamp()))
                .sidechain(false));
            seq++;
        }
        db.turns().drop(sessionId);
        db.turns().append(sessionId, turns);
        sessions.put(sessionId, doc);
    }

    private static String pickCwd(List<HistoryDoc> prompts) {
        for (var i = prompts.size() - 1; i >= 0; i--) {
            var p = prompts.get(i).project();
            if (p != null && !p.isBlank()) return p;
        }
        return null;
    }
}
