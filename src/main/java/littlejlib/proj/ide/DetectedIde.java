package littlejlib.proj.ide;

import java.nio.file.Path;

/**
 * An editor or IDE found on this machine, with the command that opens a project in it.
 * <p>
 * {@code opensFolders} separates the two kinds: an IDE takes a project directory, a text editor
 * takes a file. Handing notepad a folder does nothing useful, so the picker must not offer it as
 * a way to open a project.
 */
public class DetectedIde {

    public final String name;
    public final Path exe;
    public final String argsTemplate;
    public final boolean opensFolders;

    public DetectedIde(String name, Path exe, String argsTemplate, boolean opensFolders) {
        this.name = name;
        this.exe = exe;
        this.argsTemplate = argsTemplate;
        this.opensFolders = opensFolders;
    }

    /** A %PATH%-style command, the same shape settings use, so both routes stay interchangeable. */
    public String command() {
        return "\"" + exe.toAbsolutePath() + "\" " + argsTemplate;
    }

    @Override
    public String toString() {
        return name + " -> " + command();
    }
}
