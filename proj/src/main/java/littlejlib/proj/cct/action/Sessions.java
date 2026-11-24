package littlejlib.proj.cct.action;

import module java.base;

import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.model.SessionDoc;


public final class Sessions {

    private Sessions() {}

    public static SessionDoc exact(Db db, String sessionId) {
        return db.sessions().first(s -> Objects.equals(sessionId, s.sessionId()));
    }

    public static List<SessionDoc> byPrefix(Db db, String prefix) {
        var exact = exact(db, prefix);
        if (exact != null) return List.of(exact);
        var p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return List.of();
        var out = new ArrayList<SessionDoc>();
        for (var s : db.sessions().all()) {
            var id = s.sessionId();
            if (id != null && id.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        }
        return out;
    }

    public static List<SessionDoc> all(Db db) {
        var out = new ArrayList<SessionDoc>();
        for (var s : db.sessions().all()) out.add(s);
        return out;
    }
}
