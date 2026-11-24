package littlejlib.proj.cct.index;

import module java.base;

import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static littlejlib.proj.cct.Num.*;

/**
 * Indexes one transcript file: its turns go to the session's turn file at once, its SessionDoc into
 * {@code sessions}, which {@link Indexer} saves after all turns are written. A run that dies between
 * the two leaves a session with no (or an old) doc, and the next run rebuilds that session from zero.
 */
public final class SessionIndexer {

    private final Db db;
    private final Map<String, SessionDoc> sessions;
    private final IndexOptions opts;

    public SessionIndexer(Db db, Map<String, SessionDoc> sessions, IndexOptions opts) {
        this.db = db;
        this.sessions = sessions;
        this.opts = opts;
    }

    public IndexOutcome index(SessionFile f) {
        var prior = sessions.get(f.sessionId());
        if (unchanged(f, prior)) return IndexOutcome.UNCHANGED;
        var tail = !opts.force() && prior != null && f.sizeBytes() > n(prior.indexedSize());
        var doc = tail ? prior : blank(f);
        if (!tail) db.turns().drop(f.sessionId());
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
                if (pending.size() >= opts.batchSize()) flush(f.sessionId(), pending);
            }
            var resolved = human.resolve();
            for (var t : resolved.keep()) { acc.countTurn(t); pending.add(t); }
            flush(f.sessionId(), pending);
            db.turns().dropKeys(f.sessionId(), resolved.dropKeys());
            // Set rather than accumulated: countTurn has been counting the turns that survived this
            // pass, and on a tail pass some previously counted ones have just been deleted.
            doc.humanTurns(resolved.total());
            doc.fileBytes(f.sizeBytes()).filePath(f.path());
            acc.finish(cur.offset(), f.sizeBytes(), f.lastModified(), seq);
        } catch (IOException e) {
            return IndexOutcome.FAILED;
        }
        sessions.put(doc.sessionId(), acc.doc());
        return tail ? IndexOutcome.TAILED : IndexOutcome.REBUILT;
    }

    private void flush(String sessionId, List<TurnDoc> pending) {
        db.turns().append(sessionId, pending);
        pending.clear();
    }

    /** The human turns already indexed for this session, so a tail pass can supersede one. */
    private List<TurnDoc> humanTurnsOf(String sessionId) {
        var out = new ArrayList<TurnDoc>();
        for (var t : db.turns().of(sessionId)) if (n(t.layer()) == Layer.HUMAN.depth) out.add(t);
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
