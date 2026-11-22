package littlejlib.proj.util;

import java.nio.file.Path;

/** Opens a file in the program the system associates with it, without AWT's Desktop (which a native image cannot carry). */
public final class OpenFile {
    private OpenFile() { }

    public static boolean open(Path file) {
        var os = System.getProperty("os.name", "").toLowerCase();
        var path = file.toAbsolutePath().toString();
        if (os.contains("win")) return Cmd.detached("cmd", "/c", "start", "", path);
        return Cmd.detached(os.contains("mac") ? "open" : "xdg-open", path);
    }
}
