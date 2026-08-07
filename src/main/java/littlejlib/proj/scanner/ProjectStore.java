package littlejlib.proj.scanner;

import com.arcadedb.database.Database;
import littlejlib.proj.db.DB;
import littlejlib.proj.model.ProjectDependency;
import littlejlib.proj.model.ProjectRecord;

import java.nio.file.Path;
import java.util.List;

import static littlejlib.proj.model.ProjectRecord_A.*;
import static xyz.jphil.arcadedb.datahelper.Query.query;
import static xyz.jphil.arcadedb.datahelper.Upsert.upsert;

/**
 * Reads and writes {@link ProjectRecord} rows.
 * <p>
 * A rescan upserts every row it finds with a common stamp and then deletes only the rows of that
 * code directory left behind at an older stamp. Nothing is cleared up front, so a scan that dies
 * halfway leaves a usable cache rather than an empty one.
 */
public class ProjectStore {

    Database db() {
        return DB.get().getDatabase();
    }

    static String abs(Path p) {
        return p.toAbsolutePath().toString();
    }

    public List<ProjectRecord> load(Path codeDir) {
        return query(db(), ProjectRecord.TYPEDEF).eq($codeDir, abs(codeDir)).orderByAsc($relativePath).list();
    }

    public List<ProjectRecord> loadMavenOnly(Path codeDir) {
        return query(db(), ProjectRecord.TYPEDEF)
                .eq($codeDir, abs(codeDir)).eq($maven, true).orderByAsc($relativePath).list();
    }

    public ProjectRecord byPath(String path) {
        return query(db(), ProjectRecord.TYPEDEF).eq($path, path).firstOrNull();
    }

    public boolean hasAny(Path codeDir) {
        return query(db(), ProjectRecord.TYPEDEF).eq($codeDir, abs(codeDir)).exists();
    }

    public long count(Path codeDir) {
        return query(db(), ProjectRecord.TYPEDEF).eq($codeDir, abs(codeDir)).count();
    }

    /**
     * The fields a scan discovers. Everything else on the row belongs to somebody else - notably
     * openCount and lastOpened, which a whole-object upsert would reset to zero on every rescan,
     * quietly destroying the only ranking signal that reflects what you actually use.
     */
    static final xyz.jphil.datahelper.Field_I<ProjectRecord, ?>[] SCANNED = new xyz.jphil.datahelper.Field_I[]{
            $path, $parentPath, $codeDir, $name, $relativePath, $depth,
            $maven, $gradle, $jbang, $git,
            $groupId, $artifactId, $version, $packaging, $description,
            $gitUser, $gitOrg, $gitRepo, $gitRemote, $gitHost, $baseType, $org,
            $lastScanned, $lastModified};

    /** Single-row write, for the watcher reacting to one directory changing. */
    public void save(ProjectRecord rec) {
        var db = db();
        db.transaction(() -> rec.in(db).whereEq($path, rec.path()).fields(SCANNED).upsert());
    }

    /** Records that a project was actually picked. The heat signal behind relevance ordering. */
    public void recordUsage(String path) {
        try {
            var db = db();
            var existing = byPath(path);
            var count = existing == null ? 0 : existing.openCount();
            db.transaction(() -> upsert(db, ProjectRecord.TYPEDEF)
                    .key($path, path)
                    .set($openCount, count + 1)
                    .set($lastOpened, System.currentTimeMillis())
                    .save());
        } catch (Exception e) {
            System.err.println("[ProjectStore] could not record usage for " + path + ": " + e.getMessage());
        }
    }

    public void deleteByPath(String path) {
        var db = db();
        db.transaction(() -> db.select().fromType(ProjectRecord.__)
                .where().property($path.__).eq().value(path)
                .documents().toList().forEach(db::deleteRecord));
    }

    /** Upserts every record, then drops whatever this code directory still holds from before. */
    public void replaceAll(Path codeDir, List<ProjectRecord> records, long stamp) {
        replaceAll(codeDir, records, List.of(), stamp);
    }

    public void replaceAll(Path codeDir, List<ProjectRecord> records,
                           List<ProjectDependency> dependencies, long stamp) {
        var db = db();
        db.transaction(() -> {
            for (var rec : records) {
                rec.lastScanned(stamp);
                rec.in(db).whereEq($path, rec.path()).fields(SCANNED).upsert();
            }
        });
        deleteStale(codeDir, stamp);

        // Scoped to the maven records actually seen, NOT to the paths the dependency list happens to
        // mention: a project whose last dependency was just deleted from its pom contributes no rows
        // here, and deriving the scope from the rows would leave its old ones standing forever.
        replaceDependencies(records.stream().filter(ProjectRecord::maven)
                .map(ProjectRecord::path).toList(), dependencies);
        deleteOrphanDependencies();
    }

    // ---------- dependencies ----------

    /**
     * Rewrites the dependency rows of the named projects.
     * <p>
     * Delete-then-insert per project rather than upsert: a dependency has no stable identity of its
     * own, and the change that matters most - a dependency being REMOVED from a pom - is invisible
     * to an upsert. Scoped to the projects actually rescanned, so a partial scan never wipes rows
     * belonging to code directories it did not touch.
     */
    public void replaceDependencies(List<String> projectPaths, List<ProjectDependency> dependencies) {
        if (projectPaths.isEmpty()) return;
        var db = db();
        var byProject = new java.util.HashMap<String, List<ProjectDependency>>();
        for (var d : dependencies) byProject.computeIfAbsent(d.path(), k -> new java.util.ArrayList<>()).add(d);

        db.transaction(() -> {
            for (var projectPath : projectPaths) {
                db.select().fromType(ProjectDependency.__)
                        .where().property(littlejlib.proj.model.ProjectDependency_A.$path.__)
                        .eq().value(projectPath)
                        .documents().toList().forEach(db::deleteRecord);
                for (var d : byProject.getOrDefault(projectPath, List.of())) d.in(db).insert();
            }
        });
    }

    public List<ProjectDependency> dependenciesOf(String projectPath) {
        return query(db(), ProjectDependency.TYPEDEF)
                .eq(littlejlib.proj.model.ProjectDependency_A.$path, projectPath).list();
    }

    /** Every project that declares a dependency on these coordinates - the reverse question. */
    public List<ProjectDependency> dependentsOf(String ga) {
        return query(db(), ProjectDependency.TYPEDEF)
                .eq(littlejlib.proj.model.ProjectDependency_A.$ga, ga).list();
    }

    public List<ProjectDependency> allDependencies() {
        return query(db(), ProjectDependency.TYPEDEF).list();
    }

    // ---------- source files ----------

    /**
     * Rewrites the file rows of the named projects. Delete-then-insert for the same reason as
     * dependencies: a deleted file is invisible to an upsert, and a stale row that points at a file
     * which no longer exists is worse than no row at all.
     */
    public void replaceFiles(List<String> projectPaths,
                             List<littlejlib.proj.model.FileRecord> files) {
        if (projectPaths.isEmpty()) return;
        var db = db();
        var byProject = new java.util.HashMap<String, List<littlejlib.proj.model.FileRecord>>();
        for (var f : files) byProject.computeIfAbsent(f.projectPath(), k -> new java.util.ArrayList<>()).add(f);

        // Batched: 42,000 rows in one transaction exhausts memory, one transaction per row is slow.
        var batch = new java.util.ArrayList<String>();
        for (var p : projectPaths) {
            batch.add(p);
            if (batch.size() >= 200) {
                writeFileBatch(db, batch, byProject);
                batch = new java.util.ArrayList<>();
            }
        }
        if (!batch.isEmpty()) writeFileBatch(db, batch, byProject);
    }

    void writeFileBatch(Database db, List<String> projectPaths,
                        java.util.Map<String, List<littlejlib.proj.model.FileRecord>> byProject) {
        db.transaction(() -> {
            for (var projectPath : projectPaths) {
                db.select().fromType(littlejlib.proj.model.FileRecord.__)
                        .where().property(littlejlib.proj.model.FileRecord_A.$projectPath.__)
                        .eq().value(projectPath)
                        .documents().toList().forEach(db::deleteRecord);
                for (var f : byProject.getOrDefault(projectPath, List.of())) f.in(db).insert();
            }
        });
    }

    public List<littlejlib.proj.model.FileRecord> filesOf(String projectPath) {
        return query(db(), littlejlib.proj.model.FileRecord.TYPEDEF)
                .eq(littlejlib.proj.model.FileRecord_A.$projectPath, projectPath).list();
    }

    /** Exact stem match, which is the class-name lookup and the one that must be an index hit. */
    public List<littlejlib.proj.model.FileRecord> filesByStem(String stem) {
        return query(db(), littlejlib.proj.model.FileRecord.TYPEDEF)
                .eq(littlejlib.proj.model.FileRecord_A.$stem, stem).list();
    }

    public List<littlejlib.proj.model.FileRecord> allFiles() {
        return query(db(), littlejlib.proj.model.FileRecord.TYPEDEF).list();
    }

    public long fileCount() {
        return query(db(), littlejlib.proj.model.FileRecord.TYPEDEF).count();
    }

    /**
     * Drops dependency rows whose project no longer exists. A deleted project is removed by
     * {@link #deleteStale}, which knows nothing about dependencies, so without this the index would
     * keep answering "who depends on X" with projects that are gone.
     */
    public void deleteOrphanDependencies() {
        try {
            var known = new java.util.HashSet<String>();
            for (var r : query(db(), ProjectRecord.TYPEDEF).list()) known.add(r.path());

            var orphans = allDependencies().stream()
                    .map(ProjectDependency::path).distinct()
                    .filter(p -> p == null || !known.contains(p)).toList();
            if (orphans.isEmpty()) return;

            replaceDependencies(orphans, List.of());
        } catch (Exception e) {
            System.err.println("[ProjectStore] orphan dependency sweep failed: " + e.getMessage());
        }
    }

    void deleteStale(Path codeDir, long stamp) {
        var db = db();
        db.transaction(() -> {
            var stale = db.select().fromType(ProjectRecord.__)
                    .where().property($codeDir.__).eq().value(abs(codeDir))
                    .and().property($lastScanned.__).lt().value(stamp)
                    .documents().toList();
            stale.forEach(db::deleteRecord);
        });
    }
}
