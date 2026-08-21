package littlejlib.proj.scanner;

import littlejlib.proj.model.FileRecord;
import littlejlib.proj.model.ProjectRecord;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Walks a project's own files and records where each source file is.
 * <p>
 * Separate from {@link ProjectTreeScanner} because it is a different walk with a different budget.
 * The project scan stops at a maven project with a src/ directory - descending further would fill
 * the picker with modules nobody wants to pick. This walk starts exactly where that one stops, and
 * goes all the way down.
 * <p>
 * Extensions are a whitelist, not a blacklist. A blacklist over a code base ends up indexing build
 * output, images, jars and node_modules, and the cost of getting that wrong is paid on every scan
 * by everyone. The list below is source code plus the documents that are genuinely looked up by
 * name; anything missing can be added in one place.
 */
public class FileIndexer {

    /** Directories that never contain source worth indexing, matched by name at any depth. */
    static final Set<String> SKIP_DIRS = Set.of(
            "target", "build", "node_modules", "out", "dist", ".git", ".idea", ".vscode",
            ".gradle", ".mvn", "bin", "obj", "venv", ".venv", "__pycache__", "coverage",
            ".next", ".nuxt", "vendor", "Pods", "DerivedData");

    public static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            "java", "kt", "kts", "scala", "groovy", "clj",
            "js", "mjs", "cjs", "ts", "tsx", "jsx", "vue", "svelte",
            "py", "rb", "php", "go", "rs", "swift", "dart",
            "c", "h", "cpp", "cc", "hpp", "cs", "m", "mm",
            "sql", "sh", "bat", "ps1",
            "html", "css", "scss",
            "md", "toml", "yaml", "yml");

    final Set<String> extensions;
    int filesSeen;

    public FileIndexer() {
        this(DEFAULT_EXTENSIONS);
    }

    public FileIndexer(Set<String> extensions) {
        this.extensions = extensions;
    }

    public int filesSeen() {
        return filesSeen;
    }

    /**
     * Indexes the files of one project.
     * <p>
     * {@code stopAt} carries the paths of nested projects that are indexed in their own right, so a
     * multi-module aggregator does not also claim every file of every module. Without it a file
     * under a module would appear twice, once against the module and once against the parent, and
     * "which project is this file in" would have two answers.
     */
    public List<FileRecord> index(ProjectRecord project, Set<String> stopAt) {
        var out = new ArrayList<FileRecord>();
        var root = project.dir();
        if (root == null || !Files.isDirectory(root)) return out;

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(root)) return FileVisitResult.CONTINUE;
                    var name = dir.getFileName().toString();
                    if (SKIP_DIRS.contains(name) || name.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    // A nested project owns its own files.
                    if (stopAt.contains(dir.toAbsolutePath().toString())) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    filesSeen++;
                    var name = file.getFileName().toString();
                    var dot = name.lastIndexOf('.');
                    if (dot <= 0) return FileVisitResult.CONTINUE;

                    var ext = name.substring(dot + 1).toLowerCase();
                    if (!extensions.contains(ext)) return FileVisitResult.CONTINUE;

                    var rel = relativise(root, file);
                    out.add(new FileRecord()
                            .path(file.toAbsolutePath().toString())
                            .projectPath(project.path())
                            .name(name)
                            .stem(name.substring(0, dot))
                            .ext(ext)
                            .relPath(rel)
                            .pkg(packageOf(rel))
                            .lastModified(attrs.lastModifiedTime().toMillis()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, java.io.IOException e) {
                    return FileVisitResult.CONTINUE;   // an unreadable file is not worth failing a scan
                }
            });
        } catch (Exception e) {
            // A project we cannot walk yields no files rather than stopping the whole scan.
        }
        return out;
    }

    static String relativise(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.getFileName().toString();
        }
    }

    /**
     * The package, derived from the path rather than by reading the file.
     * <p>
     * Reading a package declaration means opening 42,000 files; the directory layout already says
     * it for every project that follows the convention, and for one that does not, a wrong guess
     * would be worse than an honest blank. So this only answers when it finds a source root it
     * recognises, and returns "" otherwise.
     */
    static String packageOf(String relPath) {
        for (var marker : List.of("src/main/java/", "src/test/java/", "src/main/kotlin/",
                "src/test/kotlin/", "src/main/scala/", "src/")) {
            var at = relPath.indexOf(marker);
            if (at < 0) continue;
            var after = relPath.substring(at + marker.length());
            var lastSlash = after.lastIndexOf('/');
            return lastSlash < 0 ? "" : after.substring(0, lastSlash).replace('/', '.');
        }
        return "";
    }
}
