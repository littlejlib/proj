package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scans all prp/ directories across all projects under a root code repos path.
 * Returns GlobalPrpEntry objects sorted by most recently modified first.
 */
public class GlobalPrpScanner {

    /**
     * PRPs of projects we already know about, newest activity first.
     * <p>
     * THE FAST PATH, and the one every caller inside the application uses. {@link #scanAllProjects}
     * finds prp folders by walking the whole directory tree, which on this machine measured 8.9s for
     * the primary code base and 15.3s for the mirror - twenty-four seconds to populate a tab someone
     * has just clicked. But proj already holds an index of where every project IS, so the walk is
     * answering a question that has already been answered: this stats {@code <project>/prp} for each
     * known project instead, and touches nothing else.
     * <p>
     * The tree walk stays, for the case this cannot serve: an empty or unbuilt index.
     */
    public static List<GlobalPrpEntry> scanProjects(java.util.Collection<Path> projectDirs) {
        var all = new ArrayList<GlobalPrpEntry>();
        for (var dir : projectDirs) {
            try {
                all.addAll(GlobalPrpEntry.forProject(dir));
            } catch (Exception e) {
                System.err.println("[GlobalPrpScanner] skipped " + dir + ": " + e);
            }
        }
        all.sort(Comparator.comparingLong(GlobalPrpEntry::getLastModified).reversed());
        return all;
    }

    /**
     * Scans all projects under codeReposPath for prp/ directories,
     * collects all PRPs, and returns them sorted by lastModified descending.
     *
     * @param codeReposPath root path where all code projects live
     * @param maxDepth maximum directory depth to search
     * @return list of GlobalPrpEntry sorted by most recently modified first
     */
    public static List<GlobalPrpEntry> scanAllProjects(Path codeReposPath, int maxDepth) {
        List<GlobalPrpEntry> allEntries = new ArrayList<>();

        // Find all directories that contain a "prp" subdirectory
        List<Path> prpDirs = findPrpDirectories(codeReposPath, maxDepth);

        for (Path prpDir : prpDirs) {
            try {
                Path projectPath = prpDir.getParent();
                String projectName = projectPath.getFileName().toString();

                List<PrpEntry> prpEntries = PrpScanner.scanPrpDirectory(prpDir);

                for (PrpEntry entry : prpEntries) {
                    allEntries.add(new GlobalPrpEntry(
                            entry, projectName, projectPath, prpDir, entry.getLastModified()));
                }
            } catch (Exception e) {
                // Exception, not IOException. One unreadable or malformed prp folder out of several
                // hundred must cost its own project's entries and nothing else - this used to let a
                // RuntimeException out of the loop and lose every project not yet reached.
                System.err.println("[GlobalPrpScanner] skipped " + prpDir + ": " + e);
            }
        }

        // Sort by lastModified descending (most recent first)
        allEntries.sort(Comparator.comparingLong(GlobalPrpEntry::getLastModified).reversed());

        return allEntries;
    }

    /**
     * Finds all directories that contain a "prp" subdirectory.
     */
    private static List<Path> findPrpDirectories(Path rootPath, int maxDepth) {
        List<Path> prpDirs = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(rootPath, maxDepth, FileVisitOption.FOLLOW_LINKS)) {
            prpDirs = walk
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals("prp"))
                    .filter(p -> {
                        // Ensure it has at least one .md file (not just an empty prp/ dir)
                        try (Stream<Path> files = Files.list(p)) {
                            return files.anyMatch(f -> f.getFileName().toString().endsWith(".md"));
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    // Skip prp dirs inside hidden directories (e.g., .git)
                    .filter(p -> {
                        Path rel = rootPath.relativize(p);
                        for (int i = 0; i < rel.getNameCount(); i++) {
                            if (rel.getName(i).toString().startsWith(".")) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("[GlobalPrpScanner] Error walking directory tree: " + e.getMessage());
        }

        return prpDirs;
    }

}
