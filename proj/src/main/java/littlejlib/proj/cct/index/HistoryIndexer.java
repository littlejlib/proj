package littlejlib.proj.cct.index;

import module java.base;

import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.model.HistoryDoc;
import littlejlib.proj.cct.store.*;

/** The typed-prompt history ({@code ~/.claude/history.jsonl}), rebuilt whole on every pass. */
public final class HistoryIndexer {

    private HistoryIndexer() {}

    public static int run(Db db) {
        var file = StorePaths.historyFile();
        if (!Files.isRegularFile(file)) return 0;
        var rows = new LinkedHashMap<String, HistoryDoc>();
        try (var cur = new LineCursor(file, 0)) {
            while (cur.next()) {
                var rec = RecordReader.read(cur.buffer(), cur.length());
                if (rec == null || rec.display() == null) continue;
                var h = row(rec);
                rows.put(h.historyKey(), h);
            }
        } catch (IOException e) {
            return 0;
        }
        db.history().replaceAll(rows.values());
        return rows.size();
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
}
