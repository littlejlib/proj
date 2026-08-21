package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PrpScanner {

    private static final Pattern MAIN_PRP_PATTERN = Pattern.compile("(\\d{2})-prp-(.+?)\\.md$");
    private static final Pattern CLOSED_PRP_PATTERN = Pattern.compile("(\\d{2})-prp-(.+?)\\.closed\\.md$");
    private static final Pattern STATUS_PATTERN = Pattern.compile("(\\d{2})-prp\\.status\\.md$");
    private static final Pattern SUB_FILE_PATTERN = Pattern.compile("(\\d{2})-prp\\.(.+)\\.md$");

    public static List<PrpEntry> scanPrpDirectory(Path prpDir) throws IOException {
        if (!Files.exists(prpDir)) {
            return Collections.emptyList();
        }

        Map<String, PrpBuilder> prpMap = new TreeMap<>();

        Files.list(prpDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    processFile(fileName, file, prpMap);
                });

        // A builder with no main file is an ORPHAN - a status file or a sub-file whose PRP has been
        // renamed or deleted, which happens and is nobody's emergency. It used to throw from
        // build(), and because that threw out of the whole scan rather than out of one entry, a
        // single orphan in one project silently emptied the global PRP list of every project after
        // it. Dropped here instead: there is no PRP to show, and that is the whole story.
        return prpMap.values().stream()
                .filter(b -> b.mainFile != null)
                .map(PrpBuilder::build)
                .sorted(Comparator.comparing(PrpEntry::getIndex))
                .collect(Collectors.toList());
    }

    private static void processFile(String fileName, Path filePath, Map<String, PrpBuilder> prpMap) {
        // Skip index files
        if (fileName.endsWith("-prp.index.md")) {
            return;
        }

        // Try closed main file first
        Matcher closedMatcher = CLOSED_PRP_PATTERN.matcher(fileName);
        if (closedMatcher.matches()) {
            String index = closedMatcher.group(1);
            String name = closedMatcher.group(2);
            prpMap.computeIfAbsent(index, k -> new PrpBuilder(index))
                    .setName(name)
                    .setStatus(PrpStatus.CLOSED)
                    .setMainFile(filePath);
            return;
        }

        // Try active main file
        Matcher mainMatcher = MAIN_PRP_PATTERN.matcher(fileName);
        if (mainMatcher.matches()) {
            String index = mainMatcher.group(1);
            String name = mainMatcher.group(2);
            prpMap.computeIfAbsent(index, k -> new PrpBuilder(index))
                    .setName(name)
                    .setStatus(PrpStatus.ACTIVE)
                    .setMainFile(filePath);
            return;
        }

        // Try status file
        Matcher statusMatcher = STATUS_PATTERN.matcher(fileName);
        if (statusMatcher.matches()) {
            String index = statusMatcher.group(1);
            prpMap.computeIfAbsent(index, k -> new PrpBuilder(index))
                    .setStatusFile(filePath);
            return;
        }

        // Try sub-file
        Matcher subMatcher = SUB_FILE_PATTERN.matcher(fileName);
        if (subMatcher.matches()) {
            String index = subMatcher.group(1);
            prpMap.computeIfAbsent(index, k -> new PrpBuilder(index))
                    .addSubFile(filePath);
        }
    }

    /**
     * Builder class to accumulate files for a single PRP
     */
    private static class PrpBuilder {
        private final String index;
        private String name;
        private PrpStatus status = PrpStatus.ACTIVE;
        private Path mainFile;
        private Path statusFile;
        private List<Path> subFiles = new ArrayList<>();

        PrpBuilder(String index) {
            this.index = index;
        }

        PrpBuilder setName(String name) {
            this.name = name;
            return this;
        }

        PrpBuilder setStatus(PrpStatus status) {
            this.status = status;
            return this;
        }

        PrpBuilder setMainFile(Path mainFile) {
            this.mainFile = mainFile;
            return this;
        }

        PrpBuilder setStatusFile(Path statusFile) {
            this.statusFile = statusFile;
            return this;
        }

        PrpBuilder addSubFile(Path subFile) {
            this.subFiles.add(subFile);
            return this;
        }

        PrpEntry build() {
            if (name == null) {
                name = "unknown";
            }
            subFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));
            return new PrpEntry(index, name, status, mainFile, statusFile, subFiles, newest());
        }

        long newest() {
            var max = mtime(mainFile);
            max = Math.max(max, mtime(statusFile));
            for (var sub : subFiles) max = Math.max(max, mtime(sub));
            return max;
        }
    }

    /** Zero for a file that is absent or unreadable, which sorts it last rather than throwing. */
    public static long mtime(Path file) {
        if (file == null) return 0L;
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
