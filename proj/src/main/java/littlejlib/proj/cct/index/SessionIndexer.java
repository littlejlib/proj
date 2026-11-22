package littlejlib.proj.cct.index;

import module java.base;

import com.arcadedb.database.Database;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static datapotter.arcadedbhelper.Query.query;
import static littlejlib.proj.cct.Num.*;
import static littlejlib.proj.cct.model.SessionDoc_A.$sessionId;

public final class SessionIndexer {

    private final Database db;
    private final IndexOptions opts;

    public SessionIndexer(Database db, IndexOptions opts) { this.db = db; this.opts = opts; }

    public IndexOutcome index(SessionFile f) {
        var prior = query(db, SessionDoc.TYPEDEF).eq($sessionId, f.sessionId()).firstOrNull();
        if (unchanged(f, prior)) return IndexOutcome.UNCHANGED;
        var tail = !opts.force() && prior != null && f.sizeBytes() > n(prior.indexedSize());
        var doc = tail ? prior : blank(f);
        if (!tail) db.transaction(() -> dropTurns(f.sessionId()));
        return consume(f, doc, tail ? n(doc.indexedOffset()) : 0L, tail);
    }

    private boolean unchanged(SessionFile f, SessionDoc prior) {
        return !opts.force() && prior != null
            && n(prior.indexedSize()) == f.sizeBytes()
            && n(prior.indexedMtime()) == f.lastModified();
    }

    private IndexOutcome consume(SessionFile f, SessionDoc doc, long from, boolean tail) {
        var acc = new SessionAccumulator(doc);
        var seq = n(doc.recordCount());
        var pending = new ArrayList<TurnDoc>(opts.batchSize());
        var human = new HumanTurns(tail ? humanTurnsOf(f.sessionId()) : List.of(), t -> {
            acc.countTurn(t);
            pending.add(t);
        });
        try (var cur = new LineCursor(Path.of(f.path()), from)) {
            while (cur.next()) {
                var rec = RecordReader.read(cur.buffer(), cur.length());
                if (rec != null) {
                    acc.apply(rec);
                    for (var t : TurnFactory.of(rec, f.sessionId(), seq, opts)) human.accept(t);
                }
                seq++;
                if (pending.size() >= opts.batchSize()) flush(pending);
            }
            var resolved = human.resolve();
            for (var t : resolved.keep()) { acc.countTurn(t); pending.add(t); }
            flush(pending);
            drop(resolved.dropKeys());
            // Set rather than accumulated: countTurn has been counting the turns that survived this
            // pass, and on a tail pass some previously counted ones have just been deleted.
            doc.humanTurns(resolved.total());
            doc.fileBytes(f.sizeBytes()).filePath(f.path());
            acc.finish(cur.offset(), f.sizeBytes(), f.lastModified(), seq);
        } catch (IOException e) {
            return IndexOutcome.FAILED;
        }
        save(acc.doc());
        return tail ? IndexOutcome.TAILED : IndexOutcome.REBUILT;
    }

    private void flush(List<TurnDoc> pending) {
        if (pending.isEmpty()) return;
        db.transaction(() -> { for (var t : pending) t.in(db).insert(); });
        pending.clear();
    }

    private void save(SessionDoc doc) {
        db.transaction(() -> doc.in(db).whereEq($sessionId, doc.sessionId()).upsert());
    }

    private void dropTurns(String sessionId) {
        db.command("sql", "DELETE FROM " + TurnDoc.__ + " WHERE sessionId = ?", sessionId);
    }

    private void drop(List<String> turnKeys) {
        if (turnKeys.isEmpty()) return;
        db.transaction(() -> {
            for (var k : turnKeys)
                db.command("sql", "DELETE FROM " + TurnDoc.__ + " WHERE turnKey = ?", k);
        });
    }

    /** The human turns already indexed for this session, so a tail pass can supersede one. */
    private List<TurnDoc> humanTurnsOf(String sessionId) {
        var out = new ArrayList<TurnDoc>();
        for (var t : query(db, TurnDoc.TYPEDEF).eq(TurnDoc_A.$sessionId, sessionId)
                .eq(TurnDoc_A.$layer, littlejlib.proj.cct.store.Layer.HUMAN.depth)) out.add(t);
        return out;
    }

    private SessionDoc blank(SessionFile f) {
        return new SessionDoc()
            .sessionId(f.sessionId())
            .filePath(f.path())
            .projectEncoded(f.projectEncoded())
            .subagent(f.subagent())
            .parentSessionId(f.parentSessionId())
            .fileBytes(f.sizeBytes());
    }
}
