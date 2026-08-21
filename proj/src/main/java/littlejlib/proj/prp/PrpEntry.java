package littlejlib.proj.prp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PrpEntry {
    private final String index;
    private final String name;
    private final PrpStatus status;
    private final Path mainFile;
    private final Path statusFile;  // Can be null
    private final List<Path> subFiles;
    // The newest mtime among every file of this PRP, which is what "recently active" means: a PRP
    // is worked on by editing its status file or dropping a log beside it far more often than by
    // editing the specification, so the main file's own date answers a different question.
    // Computed by the scanner, off the UI thread, rather than lazily on first sort.
    private final long lastModified;

    public PrpEntry(String index, String name, PrpStatus status, Path mainFile, Path statusFile,
                    List<Path> subFiles, long lastModified) {
        this.index = index;
        this.name = name;
        this.status = status;
        this.mainFile = mainFile;
        this.statusFile = statusFile;
        this.subFiles = new ArrayList<>(subFiles);
        this.lastModified = lastModified;
    }

    public String getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    public PrpStatus getStatus() {
        return status;
    }

    public Path getMainFile() {
        return mainFile;
    }

    public Path getStatusFile() {
        return statusFile;
    }

    public List<Path> getSubFiles() {
        return Collections.unmodifiableList(subFiles);
    }

    public String getDisplayName() {
        return index + ": " + name;
    }

    public boolean hasStatusFile() {
        return statusFile != null;
    }

    public int getSubFileCount() {
        return subFiles.size();
    }

    public long getLastModified() {
        return lastModified;
    }

    public List<Path> getAllFiles() {
        List<Path> all = new ArrayList<>();
        all.add(mainFile);
        if (statusFile != null) {
            all.add(statusFile);
        }
        all.addAll(subFiles);
        return all;
    }

    @Override
    public String toString() {
        return getDisplayName() + " [" + status + "]" + (getSubFileCount() > 0 ? " (" + getSubFileCount() + " sub-files)" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrpEntry prpEntry = (PrpEntry) o;
        return index.equals(prpEntry.index);
    }

    @Override
    public int hashCode() {
        return index.hashCode();
    }
}
