package littlejlib.proj.scanner;

/**
 * Progress during a filesystem scan. Called from the scanning thread, so an implementation that
 * touches the UI has to hop to the JavaFX thread itself.
 */
@FunctionalInterface
public interface ScanProgress {

    void onProgress(int foldersScanned, int projectsFound);

    ScanProgress NONE = (folders, projects) -> { };
}
