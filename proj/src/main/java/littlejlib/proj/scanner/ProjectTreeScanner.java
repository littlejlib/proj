package littlejlib.proj.scanner;

import littlejlib.proj.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Walks a code directory and produces one flat {@link ProjectRecord} per directory of interest.
 * <p>
 * Flat, not nested: structure travels in {@code parentPath}. Combines both predecessors - mm's rule
 * that a pom next to a src/ directory is a leaf rather than an aggregator to descend into, and
 * proj's dot-directory skip and non-maven project markers.
 * <p>
 * A directory with no build file and no git is a row too - see {@link #keep}. proj listed every
 * folder and mm listed only maven ones, and the merge silently took mm's answer, which lost every
 * organisation directory and every project that simply has no pom yet.
 */
public class ProjectTreeScanner {

    final int maxDepth;
    ScanProgress progress = ScanProgress.NONE;

    final AtomicInteger foldersScanned = new AtomicInteger();
    final AtomicInteger projectsFound = new AtomicInteger();
    final AtomicInteger plainFoldersFound = new AtomicInteger();

    // prp is this tool's own, never a project and never worth browsing to - it is reached by the
    // PRP button. Without it every plain folder that carries PRPs gains a second, useless row.
    static final List<String> IGNORED = List.of("target", "build", "node_modules", "out", "dist", "prp");

    public ProjectTreeScanner(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public ProjectTreeScanner progress(ScanProgress listener) {
        this.progress = listener == null ? ScanProgress.NONE : listener;
        return this;
    }

    public int foldersScanned() {
        return foldersScanned.get();
    }

    public int projectsFound() {
        return projectsFound.get();
    }

    public int plainFoldersFound() {
        return plainFoldersFound.get();
    }

    String baseType = "AUXILIARY";

    public ProjectTreeScanner baseType(String baseType) {
        if (baseType != null && !baseType.isBlank()) this.baseType = baseType;
        return this;
    }

    final List<ProjectDependency> dependencies = new ArrayList<>();

    /**
     * Every {@code <dependency>} found during the last scan, each carrying the path of the project
     * that declares it. Collected here rather than fetched later because the pom is already open
     * and already parsed at that moment - reading it a second time to answer the same question
     * would double the scan's file I/O for nothing.
     */
    public List<ProjectDependency> dependencies() {
        return dependencies;
    }

    public List<ProjectRecord> scan(Path codeDir) {
        foldersScanned.set(0);
        projectsFound.set(0);
        plainFoldersFound.set(0);
        dependencies.clear();
        var out = new ArrayList<ProjectRecord>();
        if (codeDir != null && Files.isDirectory(codeDir)) walk(codeDir, codeDir, "", 0, false, out);
        return out;
    }

    void walk(Path dir, Path codeDir, String parentPath, int depth, boolean insideProject,
              List<ProjectRecord> out) {
        for (var child : childDirs(dir)) {
            foldersScanned.incrementAndGet();

            var rec = describe(child, codeDir, parentPath, depth);
            var keep = keep(rec, insideProject);
            if (keep) {
                out.add(rec);
                (rec.isProject() ? projectsFound : plainFoldersFound).incrementAndGet();
            }

            if (foldersScanned.get() % 10 == 0) progress.onProgress(foldersScanned.get(), projectsFound.get());

            if (depth + 1 < maxDepth && !isLeafProject(rec, child)) {
                walk(child, codeDir, keep ? rec.path() : parentPath, depth + 1,
                        insideProject || rec.isProject(), out);
            }
        }
    }

    /**
     * A project is always a row. A directory with nothing in it that builds is a row too, but only
     * while the walk is still ABOVE every project - the organisation directories, and the projects
     * that have no pom or no git yet, which is what the picker was missing entirely.
     * <p>
     * Below that line the same directories are a project's own insides. Listing src/, docs/,
     * content/ and public/ as though they were projects added 413 rows to this machine's primary
     * code base and not one of them is somewhere you would ever want to open.
     */
    boolean keep(ProjectRecord rec, boolean insideProject) {
        return rec.isProject() || !insideProject;
    }

    /**
     * The same question asked from a single path rather than from the walk, for the watcher: it
     * reacts to one directory at a time and has no walk state to inherit the answer from.
     */
    public static boolean insideProject(Path dir, Path codeDir) {
        for (var p = dir.getParent(); p != null && p.startsWith(codeDir) && !p.equals(codeDir); p = p.getParent()) {
            if (isProjectDir(p)) return true;
        }
        return false;
    }

    public static boolean isProjectDir(Path dir) {
        return Files.isRegularFile(dir.resolve("pom.xml"))
                || Files.isDirectory(dir.resolve(".git"))
                || isGradle(dir)
                || JBangDetector.find(dir) != null;
    }

    static boolean isGradle(Path dir) {
        return Files.isRegularFile(dir.resolve("build.gradle"))
                || Files.isRegularFile(dir.resolve("build.gradle.kts"))
                || Files.isRegularFile(dir.resolve("settings.gradle"))
                || Files.isRegularFile(dir.resolve("settings.gradle.kts"));
    }

    ProjectRecord describe(Path dir, Path codeDir, String parentPath, int depth) {
        var rec = new ProjectRecord()
                .path(dir.toAbsolutePath().toString())
                .parentPath(parentPath)
                .codeDir(codeDir.toAbsolutePath().toString())
                .name(String.valueOf(dir.getFileName()))
                .relativePath(safeRelativize(codeDir, dir))
                .depth(depth)
                .baseType(baseType)
                .org(orgOf(safeRelativize(codeDir, dir)))
                .lastScanned(System.currentTimeMillis())
                .lastModified(mtime(dir));

        var pom = dir.resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            rec.maven(true);
            dependencies.addAll(PomReader.read(rec, pom));
        }

        rec.gradle(isGradle(dir));

        rec.jbang(JBangDetector.find(dir) != null);

        var gitDir = dir.resolve(".git");
        if (Files.isDirectory(gitDir)) {
            rec.git(true);
            GitConfigReader.applyTo(rec, gitDir);
        }

        return rec;
    }

    /**
     * A maven project with its own src/ is where the walk stops. Without this every module of every
     * project lands in the list and the picker becomes unusable.
     */
    boolean isLeafProject(ProjectRecord rec, Path dir) {
        return rec.maven() && Files.isDirectory(dir.resolve("src"));
    }

    /** The watcher's version: nothing below a leaf is ever a row, so nothing below it is worth a watch. */
    static boolean isLeafDir(Path dir) {
        return Files.isRegularFile(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve("src"));
    }

    List<Path> childDirs(Path dir) {
        try (var entries = Files.list(dir)) {
            return entries.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> !IGNORED.contains(p.getFileName().toString().toLowerCase()))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** The first segment under the code root, which is how our repositories are actually grouped. */
    static String orgOf(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return "";
        var cut = relativePath.indexOf(java.io.File.separatorChar);
        return cut < 0 ? relativePath : relativePath.substring(0, cut);
    }

    static Long mtime(Path dir) {
        try {
            return Files.getLastModifiedTime(dir).toMillis();
        } catch (Exception e) {
            return null;
        }
    }

    static String safeRelativize(Path base, Path child) {
        try {
            return base.equals(child) ? "" : base.relativize(child).toString();
        } catch (Exception e) {
            return String.valueOf(child.getFileName());
        }
    }
}
