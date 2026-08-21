package littlejlib.proj.ide;

import java.nio.file.Path;

/**
 * An editor or IDE found on this machine, with the command that opens a project in it.
 * <p>
 * {@code opensFolders} separates the two kinds: an IDE takes a project directory, a text editor
 * takes a file. Handing notepad a folder does nothing useful, so the picker must not offer it as
 * a way to open a project.
 * <p>
 * Two kinds of source, and they supply DIFFERENT things. The detector knows an executable and the
 * arguments to hand it, so its command is assembled here. A launcher configured in Settings.toml
 * supplies a WHOLE command line already - `netbeans64.exe "--open" "%PATH%"` - and assembling that
 * one produced `"netbeans64.exe" netbeans64.exe "--open" ...`, which handed NetBeans its own path
 * as the first argument. {@link #configured} is the constructor for that case.
 */
public class DetectedIde {

    public final String name;
    public final Path exe;
    public final String argsTemplate;
    public final boolean opensFolders;

    /** Set when the whole command line was supplied rather than just the arguments. */
    final String wholeCommand;

    public DetectedIde(String name, Path exe, String argsTemplate, boolean opensFolders) {
        this(name, exe, argsTemplate, opensFolders, null);
    }

    DetectedIde(String name, Path exe, String argsTemplate, boolean opensFolders, String wholeCommand) {
        this.name = name;
        this.exe = exe;
        this.argsTemplate = argsTemplate;
        this.opensFolders = opensFolders;
        this.wholeCommand = wholeCommand;
    }

    /** A launcher from Settings.toml, whose command is already complete and must not be rebuilt. */
    public static DetectedIde configured(String name, Path exe, String command) {
        return new DetectedIde(name, exe, command, true, command);
    }

    /** A %PATH%-style command, the same shape settings use, so both routes stay interchangeable. */
    public String command() {
        if (wholeCommand != null) return wholeCommand;
        return "\"" + exe.toAbsolutePath() + "\" " + argsTemplate;
    }

    /**
     * The executable's file name, lowercased - what tells a configured entry and a detected one
     * apart from a genuine second IDE. `ide.netbeans64` and the detector's "NetBeans" are the same
     * program under two names, and offering both would be two buttons that do one thing.
     */
    public String exeKey() {
        return exe == null || exe.getFileName() == null ? "" : exe.getFileName().toString().toLowerCase();
    }

    @Override
    public String toString() {
        return name + " -> " + command();
    }
}
