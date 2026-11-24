package littlejlib.proj.scanner;

import littlejlib.proj.model.FileRecord;
import littlejlib.proj.model.ProjectDependency;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.store.Tables;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads and writes {@link ProjectRecord} rows, their dependencies and their source files.
 * <p>
 * A rescan replaces what it found and drops only the rows of that code directory left behind at an
 * older stamp, so a scan that dies halfway leaves a usable cache. openCount and lastOpened are
 * never taken from a scan: they are the ranking signal and only {@link #recordUsage} writes them.
 */
public class ProjectStore {

    static String abs(Path p) {
        return p.toAbsolutePath().toString();
    }

    static final Comparator<ProjectRecord> BY_RELATIVE =
            Comparator.comparing(r -> r.relativePath() == null ? "" : r.relativePath());

    public List<ProjectRecord> load(Path codeDir) {
        var dir = abs(codeDir);
        return Tables.projects().all().stream().filter(r -> dir.equals(r.codeDir())).sorted(BY_RELATIVE).toList();
    }

    public List<ProjectRecord> loadMavenOnly(Path codeDir) {
        return load(codeDir).stream().filter(ProjectRecord::maven).toList();
    }

    public ProjectRecord byPath(String path) {
        return Tables.projects().first(r -> Objects.equals(path, r.path()));
    }

    public boolean hasAny(Path codeDir) {
        var dir = abs(codeDir);
        return Tables.projects().any(r -> dir.equals(r.codeDir()));
    }

    public long count(Path codeDir) {
        var dir = abs(codeDir);
        return Tables.projects().all().stream().filter(r -> dir.equals(r.codeDir())).count();
    }

    /** Puts a scanned row in place of the one with its path, keeping that row's usage counts. */
    static void put(List<ProjectRecord> rows, ProjectRecord rec) {
        for (var i = 0; i < rows.size(); i++) {
            var old = rows.get(i);
            if (Objects.equals(old.path(), rec.path())) {
                rec.openCount(old.openCount()).lastOpened(old.lastOpened());
                rows.set(i, rec);
                return;
            }
        }
        rows.add(rec);
    }

    /** Single-row write, for the watcher reacting to one directory changing. */
    public void save(ProjectRecord rec) {
        Tables.projects().update(rows -> put(rows, rec));
    }

    /** Records that a project was actually picked. The heat signal behind relevance ordering. */
    public void recordUsage(String path) {
        try {
            Tables.projects().update(rows -> {
                var row = rows.stream().filter(r -> Objects.equals(path, r.path())).findFirst().orElse(null);
                if (row == null) rows.add(row = new ProjectRecord().path(path));
                row.openCount(row.openCount() + 1).lastOpened(System.currentTimeMillis());
            });
        } catch (Exception e) {
            System.err.println("[ProjectStore] could not record usage for " + path + ": " + e.getMessage());
        }
    }

    public void deleteByPath(String path) {
        Tables.projects().update(rows -> rows.removeIf(r -> Objects.equals(path, r.path())));
    }

    /** Puts every record in place, then drops whatever this code directory still holds from before. */
    public void replaceAll(Path codeDir, List<ProjectRecord> records, long stamp) {
        replaceAll(codeDir, records, List.of(), stamp);
    }

    public void replaceAll(Path codeDir, List<ProjectRecord> records,
                           List<ProjectDependency> dependencies, long stamp) {
        var dir = abs(codeDir);
        List<String> gone = Tables.projects().write(rows -> {
            for (var rec : records) {
                rec.lastScanned(stamp);
                put(rows, rec);
            }
            var stale = rows.stream()
                    .filter(r -> dir.equals(r.codeDir()) && r.lastScanned() != null && r.lastScanned() < stamp)
                    .map(ProjectRecord::path).toList();
            var staleSet = new HashSet<>(stale);
            rows.removeIf(r -> staleSet.contains(r.path()));
            return stale;
        });
        if (!gone.isEmpty()) {
            var goneSet = new HashSet<>(gone);
            Tables.files().update(rows -> rows.removeIf(f -> goneSet.contains(f.projectPath())));
        }
        // Scoped to the maven records actually seen, NOT to the paths the dependency list mentions: a
        // project whose last dependency was just removed from its pom contributes no rows here.
        replaceDependencies(records.stream().filter(ProjectRecord::maven).map(ProjectRecord::path).toList(),
                dependencies);
        deleteOrphanDependencies();
    }

    // ---------- dependencies ----------

    /** Rewrites the dependency rows of the named projects; a dependency removed from a pom goes too. */
    public void replaceDependencies(List<String> projectPaths, List<ProjectDependency> dependencies) {
        if (projectPaths.isEmpty()) return;
        var scope = new HashSet<>(projectPaths);
        Tables.dependencies().update(rows -> {
            rows.removeIf(d -> scope.contains(d.path()));
            for (var d : dependencies) if (scope.contains(d.path())) rows.add(d);
        });
    }

    public List<ProjectDependency> dependenciesOf(String projectPath) {
        return Tables.dependencies().where(d -> Objects.equals(projectPath, d.path()));
    }

    /** Every project that declares a dependency on these coordinates - the reverse question. */
    public List<ProjectDependency> dependentsOf(String ga) {
        return Tables.dependencies().where(d -> Objects.equals(ga, d.ga()));
    }

    public List<ProjectDependency> allDependencies() {
        return Tables.dependencies().all();
    }

    // ---------- source files ----------

    /**
     * Rewrites the file rows of the named projects. A file path belongs to one project only, so a
     * file that moved to a new owner leaves its old owner's row behind as well.
     */
    public void replaceFiles(List<String> projectPaths, List<FileRecord> files) {
        if (projectPaths.isEmpty()) return;
        var scope = new HashSet<>(projectPaths);
        var added = new ArrayList<FileRecord>();
        for (var f : files) if (scope.contains(f.projectPath())) added.add(f);
        var addedPaths = new HashSet<String>();
        for (var f : added) addedPaths.add(f.path());
        Tables.files().update(rows -> {
            rows.removeIf(f -> scope.contains(f.projectPath()) || addedPaths.contains(f.path()));
            rows.addAll(added);
        });
    }

    /** Drops file rows whose owning project is no longer in the index. */
    public void deleteOrphanFiles() {
        try {
            var known = knownPaths();
            if (Tables.files().any(f -> f.projectPath() == null || !known.contains(f.projectPath())))
                Tables.files().update(rows -> rows.removeIf(f -> f.projectPath() == null || !known.contains(f.projectPath())));
        } catch (Exception e) {
            System.err.println("[ProjectStore] orphan file sweep failed: " + e.getMessage());
        }
    }

    public List<FileRecord> filesOf(String projectPath) {
        return Tables.files().where(f -> Objects.equals(projectPath, f.projectPath()));
    }

    /** Exact stem match: the class-name lookup. */
    public List<FileRecord> filesByStem(String stem) {
        return Tables.files().where(f -> Objects.equals(stem, f.stem()));
    }

    public List<FileRecord> allFiles() {
        return Tables.files().all();
    }

    public long fileCount() {
        return Tables.files().size();
    }

    /** Drops dependency rows whose project no longer exists, so "who depends on X" never names a gone project. */
    public void deleteOrphanDependencies() {
        try {
            var known = knownPaths();
            if (Tables.dependencies().any(d -> d.path() == null || !known.contains(d.path())))
                Tables.dependencies().update(rows -> rows.removeIf(d -> d.path() == null || !known.contains(d.path())));
        } catch (Exception e) {
            System.err.println("[ProjectStore] orphan dependency sweep failed: " + e.getMessage());
        }
    }

    static Set<String> knownPaths() {
        var known = new HashSet<String>();
        for (var r : Tables.projects().all()) known.add(r.path());
        return known;
    }
}
