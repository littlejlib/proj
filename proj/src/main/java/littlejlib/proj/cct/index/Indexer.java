package littlejlib.proj.cct.index;

import module java.base;

import com.arcadedb.database.Database;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static datapotter.arcadedbhelper.Query.query;
import static littlejlib.proj.cct.Num.*;

public final class Indexer {

    private Indexer() {}

    public static IndexReport run(Database db, IndexOptions opts, Consumer<String> progress) {
        var started = System.currentTimeMillis();
        var report = new IndexReport();
        var files = StoreScanner.scan();
        report.filesSeen(files.size());
        var indexer = new SessionIndexer(db, opts);
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
        report.orphansDropped(dropOrphans(db, files));
        progress.accept("indexing projects");
        report.projects(ProjectIndexer.run(db));
        progress.accept("indexing typed-prompt history");
        report.historyRows(HistoryIndexer.run(db, opts.batchSize()));
        progress.accept("recovering sessions whose transcript was pruned");
        report.prunedSessions(HistorySessions.rebuild(db, opts.batchSize()));
        return report.elapsedMs(System.currentTimeMillis() - started);
    }

    private static int dropOrphans(Database db, List<SessionFile> files) {
        var live = new HashSet<String>();
        for (var f : files) live.add(f.sessionId());
        var gone = new ArrayList<String>();
        for (var s : query(db, SessionDoc.TYPEDEF)) {
            if (n(s.transcriptMissing())) continue;
            if (!live.contains(s.sessionId())) gone.add(s.sessionId());
        }
        if (gone.isEmpty()) return 0;
        db.transaction(() -> {
            for (var id : gone) {
                db.command("sql", "DELETE FROM " + TurnDoc.__ + " WHERE sessionId = ?", id);
                db.command("sql", "DELETE FROM " + SessionDoc.__ + " WHERE sessionId = ?", id);
            }
        });
        return gone.size();
    }
}
