package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PrpScanner {

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
        var index = PrpFiles.numberOf(fileName);
        if (index == null) return;
        var b = prpMap.computeIfAbsent(index, PrpBuilder::new);
        var main = PrpFiles.mainMatcher(fileName);
        if (main.matches()) {
            // Closed is a status in the head (PRP 001R); the name only decides for a file whose head has none yet.
            var state = PrpHead.stateOf(filePath);
            b.setName(main.group(2)).setState(state).setStatus(state != null ? state.status() : main.group(3) != null ? PrpStatus.CLOSED : PrpStatus.ACTIVE).setMainFile(filePath);
            return;
        }
        if (fileName.endsWith(".status.md") && PrpFiles.statusMd(filePath.getParent(), index).getFileName().toString().equals(fileName)) {
            b.setStatusFile(filePath);
            return;
        }
        if (PrpFiles.isNew(fileName) && !PrpFiles.subSlot(fileName.substring(4, 6)) && PrpFiles.parseLog(fileName) == null) return;
        b.addSubFile(filePath);
    }
    /**
     * Builder class to accumulate files for a single PRP
     */
    private static class PrpBuilder {
        private final String index;
        private String name;
        private PrpStatus status = PrpStatus.ACTIVE;
        private PrpState state;
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

        PrpBuilder setState(PrpState state) {
            this.state = state;
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
            return new PrpEntry(index, name, status, state, mainFile, statusFile, subFiles, newest());
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
