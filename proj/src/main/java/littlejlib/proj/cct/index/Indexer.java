package littlejlib.proj.cct.index;

import module java.base;

import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;
import littlejlib.proj.store.Json;

import static littlejlib.proj.cct.Num.*;

public final class Indexer {

    private Indexer() {}

    public static IndexReport run(Db db, IndexOptions opts, Consumer<String> progress) {
        return db.indexing(() -> pass(db, opts, progress));
    }

    private static IndexReport pass(Db db, IndexOptions opts, Consumer<String> progress) {
        var started = System.currentTimeMillis();
        var report = new IndexReport();
        var sessions = editableCopy(db.sessions().all());
        var files = StoreScanner.scan();
        report.filesSeen(files.size());
        var indexer = new SessionIndexer(db, sessions, opts);
        var done = 0;
        for (var f : files) {
            switch (indexer.index(f)) {
                case UNCHANGED -> report.unchanged(report.unchanged() + 1);
                case TAILED -> { report.tailed(report.tailed() + 1); report.bytesRead(report.bytesRead() + f.sizeBytes()); }
                case REBUILT -> { report.rebuilt(report.rebuilt() + 1); report.bytesRead(report.bytesRead() + f.sizeBytes()); }
                case FAILED -> report.failed(report.failed() + 1);
            }
            done++;
            if (done % 10 == 0 || done == files.size())
                progress.accept("indexed " + done + "/" + files.size());
        }
        report.orphansDropped(dropOrphans(db, sessions, files));
        db.sessions().replaceAll(sessions.values());
        progress.accept("indexing projects");
        report.projects(ProjectIndexer.run(db, sessions.values()));
        progress.accept("indexing typed-prompt history");
        report.historyRows(HistoryIndexer.run(db));
        progress.accept("recovering sessions whose transcript was pruned");
        report.prunedSessions(HistorySessions.rebuild(db, sessions));
        db.sessions().replaceAll(sessions.values());
        dropStrayTurnFiles(db, sessions);
        return report.elapsedMs(System.currentTimeMillis() - started);
    }

    /** The stored docs, copied, so a pass can change them without touching what readers hold. */
    static LinkedHashMap<String, SessionDoc> editableCopy(List<SessionDoc> stored) {
        var out = new LinkedHashMap<String, SessionDoc>();
        for (var s : stored) out.put(s.sessionId(), Json.GSON.fromJson(Json.GSON.toJson(s), SessionDoc.class));
        return out;
    }

    private static int dropOrphans(Db db, Map<String, SessionDoc> sessions, List<SessionFile> files) {
        var live = new HashSet<String>();
        for (var f : files) live.add(f.sessionId());
        var gone = new ArrayList<String>();
        for (var s : sessions.values()) {
            if (n(s.transcriptMissing())) continue;
            if (!live.contains(s.sessionId())) gone.add(s.sessionId());
        }
        for (var id : gone) {
            db.turns().drop(id);
            sessions.remove(id);
        }
        return gone.size();
    }

    /** Turn files with no session doc: left by a pass that died before saving its docs. */
    private static void dropStrayTurnFiles(Db db, Map<String, SessionDoc> sessions) {
        for (var id : db.turns().sessionIds()) if (!sessions.containsKey(id)) db.turns().drop(id);
    }
}
