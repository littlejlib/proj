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
                    long lastModified = getLastModifiedTime(entry);
                    GlobalPrpEntry globalEntry = new GlobalPrpEntry(
                            entry, projectName, projectPath, prpDir, lastModified);
                    allEntries.add(globalEntry);
                }
            } catch (IOException e) {
                System.err.println("[GlobalPrpScanner] Error scanning " + prpDir + ": " + e.getMessage());
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

    /**
     * Gets the last modified time of a PRP entry.
     * Uses the most recent modification time among all files in the PRP.
     */
    private static long getLastModifiedTime(PrpEntry entry) {
        long maxTime = 0;

        // Check main file
        maxTime = Math.max(maxTime, getFileModifiedTime(entry.getMainFile()));

        // Check status file
        if (entry.hasStatusFile()) {
            maxTime = Math.max(maxTime, getFileModifiedTime(entry.getStatusFile()));
        }

        // Check sub-files
        for (Path subFile : entry.getSubFiles()) {
            maxTime = Math.max(maxTime, getFileModifiedTime(subFile));
        }

        return maxTime;
    }

    private static long getFileModifiedTime(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
