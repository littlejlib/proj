package littlejlib.proj.workspace;

import com.arcadedb.database.Database;
import littlejlib.proj.db.DB;

import java.nio.file.Path;
import java.util.List;

import static littlejlib.proj.workspace.WorkspaceRecord_A.*;
import static datapotter.arcadedbhelper.Query.query;
import static datapotter.arcadedbhelper.Upsert.upsert;

/**
 * Reads and writes {@link WorkspaceRecord} rows.
 */
public class WorkspaceStore {

    Database db() {
        return DB.get().getDatabase();
    }

    static String abs(Path p) {
        return p.toAbsolutePath().toString();
    }

    static final datapotter.datahelper.Field_I<WorkspaceRecord, ?>[] SCANNED =
            new datapotter.datahelper.Field_I[]{
                    $path, $parentPath, $base, $groupSlug, $name, $relativePath, $depth,
                    $hasPrp, $dated, $container, $prpCount, $lastScanned, $lastActivity};

    public List<WorkspaceRecord> load(Path base) {
        return query(db(), WorkspaceRecord.TYPEDEF).eq($base, abs(base)).orderByAsc($relativePath).list();
    }

    public List<WorkspaceRecord> loadGroup(String groupSlug) {
        return query(db(), WorkspaceRecord.TYPEDEF).eq($groupSlug, groupSlug)
                .orderByAsc($relativePath).list();
    }

    /** When this group was last written by a scan, or 0 when it has never been scanned. */
    public long lastScanned(String groupSlug) {
        var newest = query(db(), WorkspaceRecord.TYPEDEF).eq($groupSlug, groupSlug)
                .orderByDesc($lastScanned).limit(1).firstOrNull();
        return newest == null || newest.lastScanned() == null ? 0L : newest.lastScanned();
    }

    public WorkspaceRecord byPath(String path) {
        return query(db(), WorkspaceRecord.TYPEDEF).eq($path, path).firstOrNull();
    }

    public long count(Path base) {
        return query(db(), WorkspaceRecord.TYPEDEF).eq($base, abs(base)).count();
    }

    public void replaceAll(Path base, List<WorkspaceRecord> records, long stamp) {
        var db = db();
        db.transaction(() -> {
            for (var rec : records) {
                rec.lastScanned(stamp);
                rec.in(db).whereEq($path, rec.path()).fields(SCANNED).upsert();
            }
        });
        deleteStale(base, stamp);
    }

    /** Rows of this base left behind at an older stamp - folders whose PRPs are gone, or renamed. */
    void deleteStale(Path base, long stamp) {
        var db = db();
        var gone = query(db, WorkspaceRecord.TYPEDEF).eq($base, abs(base)).list().stream()
                .filter(r -> r.lastScanned() == null || r.lastScanned() < stamp)
                .map(WorkspaceRecord::path).toList();
        if (gone.isEmpty()) return;
        db.transaction(() -> gone.forEach(p -> db.select().fromType(WorkspaceRecord.__)
                .where().property($path.__).eq().value(p)
                .documents().toList().forEach(db::deleteRecord)));
    }

    /** Records that a folder was actually picked, so heat ordering reflects use and not only mtime. */
    public void recordUsage(String path) {
        try {
            var db = db();
            var existing = byPath(path);
            var count = existing == null ? 0 : existing.openCount();
            db.transaction(() -> upsert(db, WorkspaceRecord.TYPEDEF)
                    .key($path, path)
                    .set($openCount, count + 1)
                    .set($lastOpened, System.currentTimeMillis())
                    .save());
        } catch (Exception e) {
            System.err.println("[WorkspaceStore] could not record usage for " + path + ": " + e.getMessage());
        }
    }
}
