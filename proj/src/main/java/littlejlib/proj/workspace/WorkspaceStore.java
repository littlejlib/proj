package littlejlib.proj.workspace;

import littlejlib.proj.store.Tables;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Reads and writes {@link WorkspaceRecord} rows.
 */
public class WorkspaceStore {

    static String abs(Path p) {
        return p.toAbsolutePath().toString();
    }

    static final Comparator<WorkspaceRecord> BY_RELATIVE =
            Comparator.comparing(r -> r.relativePath() == null ? "" : r.relativePath());

    public List<WorkspaceRecord> load(Path base) {
        var b = abs(base);
        return Tables.workspaces().all().stream().filter(r -> b.equals(r.base())).sorted(BY_RELATIVE).toList();
    }

    public List<WorkspaceRecord> loadGroup(String groupSlug) {
        return Tables.workspaces().all().stream().filter(r -> Objects.equals(groupSlug, r.groupSlug()))
                .sorted(BY_RELATIVE).toList();
    }

    /** When this group was last written by a scan, or 0 when it has never been scanned. */
    public long lastScanned(String groupSlug) {
        var newest = 0L;
        for (var r : Tables.workspaces().all())
            if (Objects.equals(groupSlug, r.groupSlug()) && r.lastScanned() != null)
                newest = Math.max(newest, r.lastScanned());
        return newest;
    }

    public WorkspaceRecord byPath(String path) {
        return Tables.workspaces().first(r -> Objects.equals(path, r.path()));
    }

    public long count(Path base) {
        var b = abs(base);
        return Tables.workspaces().all().stream().filter(r -> b.equals(r.base())).count();
    }

    /** Puts every record in place (keeping usage counts), then drops this base's rows from older scans. */
    public void replaceAll(Path base, List<WorkspaceRecord> records, long stamp) {
        var b = abs(base);
        Tables.workspaces().update(rows -> {
            for (var rec : records) {
                rec.lastScanned(stamp);
                var i = indexOf(rows, rec.path());
                if (i < 0) rows.add(rec);
                else {
                    var old = rows.get(i);
                    rows.set(i, rec.openCount(old.openCount()).lastOpened(old.lastOpened()));
                }
            }
            rows.removeIf(r -> b.equals(r.base()) && (r.lastScanned() == null || r.lastScanned() < stamp));
        });
    }

    static int indexOf(List<WorkspaceRecord> rows, String path) {
        for (var i = 0; i < rows.size(); i++) if (Objects.equals(path, rows.get(i).path())) return i;
        return -1;
    }

    /** Records that a folder was actually picked, so heat ordering reflects use and not only mtime. */
    public void recordUsage(String path) {
        try {
            Tables.workspaces().update(rows -> {
                var i = indexOf(rows, path);
                var row = i < 0 ? new WorkspaceRecord().path(path) : rows.get(i);
                if (i < 0) rows.add(row);
                row.openCount(row.openCount() + 1).lastOpened(System.currentTimeMillis());
            });
        } catch (Exception e) {
            System.err.println("[WorkspaceStore] could not record usage for " + path + ": " + e.getMessage());
        }
    }
}
