package littlejlib.proj;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds git. A shell started before git went on PATH still has the old PATH, so on Windows the
 * registry's current PATH is consulted when the inherited one has no git.
 */
public final class GitExe {

    private GitExe() { }

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("win");
    private static volatile Path cached;

    /** A ProcessBuilder for {@code git args...}, with git's own folder at the front of the child's PATH. */
    public static ProcessBuilder command(String... args) {
        var git = find();
        var cmd = new ArrayList<String>();
        cmd.add(git == null ? "git" : git.toString());
        cmd.addAll(List.of(args));
        var pb = new ProcessBuilder(cmd);
        if (git != null) {
            var env = pb.environment();
            var key = env.keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
            var old = env.getOrDefault(key, "");
            env.put(key, git.getParent() + (old.isEmpty() ? "" : File.pathSeparator + old));
        }
        return pb;
    }

    /** The git executable, or null when neither PATH nor the registry knows one. */
    public static Path find() {
        var c = cached;
        if (c != null) return c;
        c = search(System.getenv("PATH"));
        if (c == null && WINDOWS) c = search(registryPath("HKCU\\Environment") + ";"
                + registryPath("HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment"));
        return cached = c;
    }

    private static Path search(String path) {
        if (path == null) return null;
        var name = WINDOWS ? "git.exe" : "git";
        for (var dir : path.split(File.pathSeparator)) {
            dir = dir.trim().replace("\"", "");
            if (dir.isEmpty()) continue;
            try {
                var p = Path.of(dir, name);
                if (Files.isRegularFile(p)) return p;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static final Pattern VALUE = Pattern.compile("^\\s+Path\\s+REG_(?:EXPAND_)?SZ\\s+(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VAR = Pattern.compile("%([^%]+)%");

    private static String registryPath(String key) {
        try {
            var p = new ProcessBuilder("reg", "query", key, "/v", "Path").redirectErrorStream(true).start();
            var out = new String(p.getInputStream().readAllBytes(), Charset.defaultCharset());
            p.waitFor();
            for (var line : out.split("\\R")) {
                var m = VALUE.matcher(line);
                if (m.matches()) {
                    return VAR.matcher(m.group(1)).replaceAll(r -> {
                        var v = System.getenv(r.group(1));
                        return java.util.regex.Matcher.quoteReplacement(v == null ? r.group() : v);
                    });
                }
            }
        } catch (Exception ignored) { }
        return "";
    }
}
