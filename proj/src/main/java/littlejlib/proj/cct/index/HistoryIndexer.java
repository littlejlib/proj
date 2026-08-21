package littlejlib.proj.cct.index;

import module java.base;

import com.arcadedb.database.Database;
import littlejlib.proj.cct.model.HistoryDoc;
import littlejlib.proj.cct.store.*;

import static littlejlib.proj.cct.model.HistoryDoc_A.$historyKey;

public final class HistoryIndexer {

    private HistoryIndexer() {}

    public static int run(Database db, int batchSize) {
        var file = StorePaths.historyFile();
        if (!Files.isRegularFile(file)) return 0;
        db.transaction(() -> db.command("sql", "DELETE FROM " + HistoryDoc.__));
        var written = 0;
        var pending = new ArrayList<HistoryDoc>(batchSize);
        try (var cur = new LineCursor(file, 0)) {
            while (cur.next()) {
                var rec = RecordReader.read(cur.buffer(), cur.length());
                if (rec == null || rec.display() == null) continue;
                pending.add(row(rec));
                if (pending.size() >= batchSize) written += flush(db, pending);
            }
            written += flush(db, pending);
        } catch (IOException e) {
            return written;
        }
        return written;
    }

    private static HistoryDoc row(RawRecord r) {
        var display = r.display();
        return new HistoryDoc()
            .historyKey(r.sessionId() + "#" + r.timestampMillis() + "#" + display.hashCode())
            .sessionId(r.sessionId())
            .project(r.project())
            .display(display)
            .timestamp(r.timestampMillis());
    }

    private static int flush(Database db, List<HistoryDoc> pending) {
        if (pending.isEmpty()) return 0;
        var n = pending.size();
        db.transaction(() -> {
            for (var h : pending) h.in(db).whereEq($historyKey, h.historyKey()).upsert();
        });
        pending.clear();
        return n;
    }
}
