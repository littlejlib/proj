package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The {@code prp} folders at and below a folder, to a chosen depth: 0 is the folder alone. */
public final class PrpDirFinder {

    private PrpDirFinder() { }

    static final Set<String> SKIP = Set.of(".git", "node_modules", "target", "build", ".idea");

    public static List<Path> find(Path root, int depth) throws IOException {
        var base = root.toAbsolutePath().normalize();
        var found = new ArrayList<Path>();
        if ("prp".equals(String.valueOf(base.getFileName()))) {
            found.add(base);
            return found;
        }
        Files.walkFileTree(base, Set.of(), depth + 2, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                var name = String.valueOf(dir.getFileName());
                if (!dir.equals(base) && SKIP.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                if ("prp".equals(name)) {
                    found.add(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        return found;
    }
}
