package littlejlib.proj.workspace;

import littlejlib.proj.scanner.ScanProgress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Walks one base of a PLAIN group and produces a row per folder that carries PRPs, plus the
 * ancestors needed to reach them.
 */
public class WorkspaceScanner {

    static final List<String> IGNORED =
            List.of(PrpFolders.PRP_DIR, "target", "build", "node_modules", "out", "dist");

    final int maxDepth;
    String groupSlug = "";
    ScanProgress progress = ScanProgress.NONE;

    final AtomicInteger foldersScanned = new AtomicInteger();
    final AtomicInteger foldersFound = new AtomicInteger();

    public WorkspaceScanner(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public WorkspaceScanner group(String slug) {
        if (slug != null) this.groupSlug = slug;
        return this;
    }

    public WorkspaceScanner progress(ScanProgress listener) {
        this.progress = listener == null ? ScanProgress.NONE : listener;
        return this;
    }

    public int foldersScanned() {
        return foldersScanned.get();
    }

    public int foldersFound() {
        return foldersFound.get();
    }

    public List<WorkspaceRecord> scan(Path base) {
        foldersScanned.set(0);
        foldersFound.set(0);
        if (base == null || !Files.isDirectory(base)) return List.of();

        var seen = new LinkedHashMap<String, WorkspaceRecord>();
        visit(base, 0, seen);
        return rows(base, seen);
    }

    /**
     * Above the depth limit the listing is owed anyway, so prp comes out of it; at the limit prp is
     * probed instead. Listing the deepest level too cost 16.7s against 9s on a network base.
     */
    void visit(Path dir, int depth, Map<String, WorkspaceRecord> seen) {
        var descend = depth < maxDepth;
        var children = descend ? subdirs(dir) : List.<Path>of();
        if (depth > 0) {
            foldersScanned.incrementAndGet();
            if (foldersScanned.get() % 25 == 0) progress.onProgress(foldersScanned.get(), seen.size());
            var prp = descend
                    ? children.stream().filter(PrpFolders::isPrpDir).findFirst().orElse(null)
                    : PrpFolders.probe(dir);
            // Every folder is a row. One you cannot see is one you cannot put a PRP into.
            var rec = new WorkspaceRecord();
            PrpFolders.describe(prp, rec);
            seen.put(dir.toAbsolutePath().toString(), rec);
        }
        for (var child : children) {
            if (!ignored(child)) visit(child, depth + 1, seen);
        }
    }

    static boolean ignored(Path dir) {
        var n = dir.getFileName().toString();
        return n.startsWith(".") || IGNORED.contains(n.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * The qualifying folders plus every ancestor between them and the base, each pointed at its
     * nearest kept ancestor. Keyed on the path, so a folder that both qualifies and is somebody
     * else's ancestor is one row rather than two.
     */
    List<WorkspaceRecord> rows(Path base, Map<String, WorkspaceRecord> seen) {
        var kept = new LinkedHashMap<String, WorkspaceRecord>();
        seen.forEach((key, described) -> kept.put(key, fill(Path.of(key), base, described)));
        for (var rec : kept.values()) rec.parentPath(nearestKept(rec.dir().getParent(), base, kept));
        rollUp(kept);
        markContainers(kept);
        foldersFound.set((int) kept.values().stream().filter(WorkspaceRecord::isProject).count());
        return new ArrayList<>(kept.values());
    }

    /**
     * A folder is at least as recent as the newest thing under it. Without this an ancestor kept
     * only for its children carries its own directory mtime, so a node holding this week's work
     * sorts to the bottom of a list ordered by heat and the work under it is never seen.
     */
    static void rollUp(Map<String, WorkspaceRecord> kept) {
        kept.values().stream()
                .sorted(java.util.Comparator.comparingInt((WorkspaceRecord r) -> r.depth()).reversed())
                .forEach(rec -> {
                    var parent = kept.get(rec.parentPath());
                    if (parent != null && rec.activity() > parent.activity()) parent.lastActivity(rec.activity());
                });
    }

    /**
     * A folder holding projects is a CONTAINER - a repo of projects rather than a project.
     */
    static void markContainers(Map<String, WorkspaceRecord> kept) {
        kept.values().stream()
                .sorted(java.util.Comparator.comparingInt((WorkspaceRecord r) -> r.depth()).reversed())
                .forEach(rec -> {
                    if (!rec.isProject() && !rec.container()) return;
                    var parent = kept.get(rec.parentPath());
                    if (parent != null) parent.container(true);
                });
    }

    WorkspaceRecord fill(Path dir, Path base, WorkspaceRecord described) {
        var rec = described == null ? new WorkspaceRecord() : described;
        var relative = base.relativize(dir);
        rec.path(dir.toAbsolutePath().toString())
                .base(base.toAbsolutePath().toString())
                .groupSlug(groupSlug)
                .name(dir.getFileName() == null ? dir.toString() : dir.getFileName().toString())
                .relativePath(relative.toString())
                .depth(relative.getNameCount());
        rec.dated(FolderNames.dated(rec.name()));
        if (rec.lastActivity() == null) rec.lastActivity(PrpFolders.modified(dir));
        return rec;
    }

    static String nearestKept(Path from, Path base, Map<String, WorkspaceRecord> kept) {
        for (var p = from; p != null && !p.equals(base) && p.startsWith(base); p = p.getParent()) {
            var key = p.toAbsolutePath().toString();
            if (kept.containsKey(key)) return key;
        }
        return "";
    }

    /** Every subdirectory, prp and the ignored ones included - {@link #visit} needs to SEE prp. */
    static List<Path> subdirs(Path dir) {
        try (var list = Files.list(dir)) {
            return list.filter(Files::isDirectory).sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
