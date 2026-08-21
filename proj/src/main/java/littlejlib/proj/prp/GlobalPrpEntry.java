package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A PRP together with the project it belongs to.
 * <p>
 * Written for the global viewer, and now the handle EVERY PRP view uses, one project's or all of
 * them - because the moment a PRP list can be shown beside a project list, in a popup, and in a
 * global tab, the thing being listed has to carry its own project or half the actions cannot be
 * performed on it. {@code @prp/...} is relative to a project root, the agent has to start in a
 * project root, and a bare {@link PrpEntry} knows neither.
 * <p>
 * The name is historical: "global" here means "carries its project context", not "comes from the
 * global scan". {@link #forProject} builds the same objects for a single project.
 */
public class GlobalPrpEntry {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PrpEntry prpEntry;
    private final String projectName;
    private final Path projectPath;
    private final Path prpDir;
    private final long lastModified;
    private String cachedContent;

    public GlobalPrpEntry(PrpEntry prpEntry, String projectName, Path projectPath, Path prpDir, long lastModified) {
        this.prpEntry = prpEntry;
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.prpDir = prpDir;
        this.lastModified = lastModified;
    }

    /**
     * Every PRP of one project. The prp folder is not required to exist - a project that has never
     * had one is an empty list rather than an error, which is what the side panel shows for most
     * of the rows in the picker.
     */
    public static java.util.List<GlobalPrpEntry> forProject(Path projectDir) throws IOException {
        var prpDir = PrpCreator.prpDirOf(projectDir);
        if (!Files.isDirectory(prpDir)) return java.util.List.of();
        var name = projectDir.getFileName() == null
                ? projectDir.toString() : projectDir.getFileName().toString();
        return PrpScanner.scanPrpDirectory(prpDir).stream()
                .map(e -> new GlobalPrpEntry(e, name, projectDir, prpDir, e.getLastModified()))
                .toList();
    }

    public PrpEntry getPrpEntry() {
        return prpEntry;
    }

    /** Delegates, because every caller that holds one of these wants the PRP's own fields. */
    public String getIndex() {
        return prpEntry.getIndex();
    }

    public String getName() {
        return prpEntry.getName();
    }

    public PrpStatus getStatus() {
        return prpEntry.getStatus();
    }

    public Path getMainFile() {
        return prpEntry.getMainFile();
    }

    public String getProjectName() {
        return projectName;
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public Path getPrpDir() {
        return prpDir;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getLastModifiedDisplay() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(lastModified), ZoneId.systemDefault());
        return DATE_FORMAT.format(dateTime);
    }

    /**
     * Lazily loads and returns the full content of the main PRP file.
     * Returns empty string on error.
     */
    public String getFullContent() {
        if (cachedContent == null) {
            try {
                cachedContent = new String(Files.readAllBytes(prpEntry.getMainFile()));
            } catch (IOException e) {
                cachedContent = "(Error reading file: " + e.getMessage() + ")";
            }
        }
        return cachedContent;
    }

    public String getDisplayName() {
        return projectName + " / " + prpEntry.getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName() + " [" + prpEntry.getStatus() + "] " + getLastModifiedDisplay();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GlobalPrpEntry that = (GlobalPrpEntry) o;
        return prpEntry.equals(that.prpEntry) && projectPath.equals(that.projectPath);
    }

    @Override
    public int hashCode() {
        return 31 * prpEntry.hashCode() + projectPath.hashCode();
    }
}
