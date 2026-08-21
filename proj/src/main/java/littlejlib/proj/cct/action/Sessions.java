package littlejlib.proj.cct.action;

import module java.base;

import com.arcadedb.database.Database;
import littlejlib.proj.cct.model.SessionDoc;

import static xyz.jphil.arcadedb.datahelper.Query.query;
import static littlejlib.proj.cct.model.SessionDoc_A.$sessionId;

public final class Sessions {

    private Sessions() {}

    public static SessionDoc exact(Database db, String sessionId) {
        return query(db, SessionDoc.TYPEDEF).eq($sessionId, sessionId).firstOrNull();
    }

    public static List<SessionDoc> byPrefix(Database db, String prefix) {
        var exact = exact(db, prefix);
        if (exact != null) return List.of(exact);
        var p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return List.of();
        var out = new ArrayList<SessionDoc>();
        for (var s : query(db, SessionDoc.TYPEDEF)) {
            var id = s.sessionId();
            if (id != null && id.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        }
        return out;
    }

    public static List<SessionDoc> all(Database db) {
        var out = new ArrayList<SessionDoc>();
        for (var s : query(db, SessionDoc.TYPEDEF)) out.add(s);
        return out;
    }
}
