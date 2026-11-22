package littlejlib.proj.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Finding Clink, and finding where IT thinks scripts belong.
 * <p>
 * The directory is asked of {@code clink info} rather than assumed, because Clink's script path is
 * configurable and {@code clink installscripts} can add more. Of the paths it reports we take the
 * one under the user's profile: the other is Clink's own binaries directory, and a file left there
 * is lost the next time Clink updates itself.
 */
public final class Clink {

    private Clink() { }

    public static final String SCRIPT = "proj.lua";

    public static boolean installed() {
        return exe() != null;
    }

    /** clink.bat is the documented entry point; the raw exe is the fallback. */
    public static String exe() {
        for (var candidate : List.of("clink.bat", "clink_x64.exe", "clink.exe")) {
            var found = onPath(candidate);
            if (found != null) return found;
        }
        return null;
    }

    static String onPath(String name) {
        var path = System.getenv("PATH");
        if (path == null) return null;
        for (var dir : path.split(";")) {
            if (dir.isBlank()) continue;
            try {
                var p = Path.of(dir.trim(), name);
                if (Files.isRegularFile(p)) return p.toString();
            } catch (Exception ignored) {
                // A malformed PATH entry is not worth failing over.
            }
        }
        return null;
    }

    /**
     * Where to put the script. Asks Clink, prefers a per-user directory, and falls back to
     * %LOCALAPPDATA%\clink, which is where Clink keeps its own state anyway.
     */
    public static Path scriptDir() {
        for (var dir : reportedScriptDirs()) {
            var local = System.getenv("LOCALAPPDATA");
            var appdata = System.getenv("APPDATA");
            var s = dir.toString().toLowerCase();
            if (local != null && s.startsWith(local.toLowerCase())) return dir;
            if (appdata != null && s.startsWith(appdata.toLowerCase())) return dir;
        }
        var local = System.getenv("LOCALAPPDATA");
        return local == null ? null : Path.of(local, "clink");
    }

    /** Parses the `scripts : a ; b` line out of `clink info`. */
    public static List<Path> reportedScriptDirs() {
        var dirs = new ArrayList<Path>();
        var exe = exe();
        if (exe == null) return dirs;
        try {
            var pb = new ProcessBuilder(exe, "info");
            pb.redirectErrorStream(true);
            var proc = pb.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    var colon = line.indexOf(':');
                    if (colon < 0 || !line.substring(0, colon).trim().equalsIgnoreCase("scripts")) continue;
                    for (var part : line.substring(colon + 1).split(";")) {
                        if (!part.isBlank()) dirs.add(Path.of(part.trim()));
                    }
                    break;
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            Thread.interrupted();
        }
        return dirs;
    }

    public static Path installedScript() {
        var dir = scriptDir();
        return dir == null ? null : dir.resolve(SCRIPT);
    }
}
