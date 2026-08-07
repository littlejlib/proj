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

    public PrpEntry(String index, String name, PrpStatus status, Path mainFile, Path statusFile, List<Path> subFiles) {
        this.index = index;
        this.name = name;
        this.status = status;
        this.mainFile = mainFile;
        this.statusFile = statusFile;
        this.subFiles = new ArrayList<>(subFiles);
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
