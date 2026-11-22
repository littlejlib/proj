package littlejlib.proj.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Finding an installed executable, shared by the IDE and terminal detectors.
 * <p>
 * PATH first, then the usual install locations, then - and this is the part that was missing - the
 * SIBLINGS of directories already on PATH. Tools installed outside the standard locations are
 * usually installed together: this machine keeps its command-line tools in {@code C:/user/Apps/cmdtools},
 * which is on PATH, and Tabby in {@code C:/user/Apps/tabby}, which is not on PATH, not under Program
 * Files and not under LOCALAPPDATA/Programs. Nothing in the old search would ever have found it, and
 * the user would have been told their terminal was not installed while it ran in front of them.
 * <p>
 * (Backslashes are avoided in this comment on purpose: javac expands {@code \\u} escapes before it
 * lexes, so a Windows path in a comment is a compile error waiting to happen.)
 * <p>
 * The search is bounded: one level of siblings, plus each candidate's own bin/, which is what
 * NetBeans and the JetBrains toolbox need. It stats a few hundred paths once per process.
 */
public final class ToolLocator {

    private ToolLocator() { }

    public static Path locate(List<String> executableNames) {
        for (var name : executableNames) {
            var onPath = onPath(name);
            if (onPath != null) return onPath;
        }
        for (var name : executableNames) {
            for (var dir : searchDirs()) {
                var direct = dir.resolve(name);
                if (isProgram(direct)) return direct;
                var nested = findWithin(dir, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    /**
     * "Is there a program here", which on Windows is NOT the same question as "is there a file
     * here".
     * <p>
     * A Store app installs an APP EXECUTION ALIAS into {@code %LOCALAPPDATA%\Microsoft\WindowsApps}
     * - a zero-length reparse point the shell resolves and the filesystem API does not. Measured on
     * {@code wt.exe}: {@code Files.exists} false, {@code isRegularFile} false, the same with
     * NOFOLLOW_LINKS, {@code isSymbolicLink} false, {@code Files.size} throws "The file cannot be
     * accessed by the system" - and {@code isExecutable} TRUE. So an existence test says the file is
     * not there while the shell runs it perfectly well.
     * <p>
     * That is exactly how Windows Terminal came to be undetectable on a machine with it on PATH,
     * having been listed in this application's own terminal table since PRP-14 and never once
     * offered. Everything from the Store is affected, not just this one.
     */
    public static boolean isProgram(Path candidate) {
        if (candidate == null) return false;
        try {
            if (Files.isRegularFile(candidate)) return true;
            return !Files.isDirectory(candidate) && Files.isExecutable(candidate);
        } catch (Exception e) {
            return false;
        }
    }

    public static Path onPath(String name) {
        var path = System.getenv("PATH");
        if (path == null) return null;
        for (var entry : path.split(java.io.File.pathSeparator)) {
            try {
                var candidate = Path.of(entry.trim()).resolve(name);
                if (isProgram(candidate)) return candidate;
            } catch (Exception e) {
                // an unusable PATH entry is not worth reporting
            }
        }
        return null;
    }

    /** One level of subdirectories plus their bin/, which covers NetBeans and JetBrains layouts. */
    public static Path findWithin(Path dir, String name) {
        if (!Files.isDirectory(dir)) return null;
        try (var children = Files.list(dir)) {
            for (var child : children.filter(Files::isDirectory).toList()) {
                var direct = child.resolve(name);
                if (isProgram(direct)) return direct;
                var bin = child.resolve("bin").resolve(name);
                if (isProgram(bin)) return bin;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    static volatile List<Path> cachedDirs;

    public static List<Path> searchDirs() {
        var local = cachedDirs;
        if (local == null) {
            local = buildSearchDirs();
            cachedDirs = local;
        }
        return local;
    }

    static List<Path> buildSearchDirs() {
        var dirs = new LinkedHashSet<Path>();

        for (var env : List.of("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA", "APPDATA")) {
            var value = System.getenv(env);
            if (value == null || value.isBlank()) continue;
            dirs.add(Path.of(value));
            dirs.add(Path.of(value, "Programs"));
            dirs.add(Path.of(value, "JetBrains"));
            dirs.add(Path.of(value, "JetBrains", "Toolbox", "apps"));
        }

        var windir = System.getenv("SystemRoot");
        if (windir != null) dirs.add(Path.of(windir, "System32"));

        // The siblings of every PATH entry. An application installed beside a directory the user
        // already put on PATH is installed where that user keeps applications.
        var path = System.getenv("PATH");
        if (path != null) {
            for (var entry : path.split(java.io.File.pathSeparator)) {
                try {
                    var parent = Path.of(entry.trim()).getParent();
                    if (parent != null && Files.isDirectory(parent)) dirs.add(parent);
                } catch (Exception e) {
                    // an unusable PATH entry is not worth reporting
                }
            }
        }

        // /opt and /usr/local matter on the mac and linux side, where PRP 20 is heading.
        for (var unix : List.of("/opt", "/usr/local/bin", "/Applications")) {
            try {
                var p = Path.of(unix);
                if (Files.isDirectory(p)) dirs.add(p);
            } catch (Exception e) {
                // not this platform
            }
        }

        return new ArrayList<>(dirs);
    }

    public static void forget() {
        cachedDirs = null;
    }
}
