package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Extended PRP entry that includes project context and last-modified metadata.
 * Used by the Global PRP viewer to display PRPs from all projects.
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

    public PrpEntry getPrpEntry() {
        return prpEntry;
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
