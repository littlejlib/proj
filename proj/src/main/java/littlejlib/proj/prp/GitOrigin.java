package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** The GitHub organisation a folder's {@code origin} points at, read from {@code .git/config}. */
public final class GitOrigin {

    private GitOrigin() { }

    static final Pattern URL = Pattern.compile("github\\.com[:/]([^/\\s]+)/");

    /** Null when the folder is not a clone of a GitHub repository. */
    public static String org(Path project) {
        try {
            var config = project.resolve(".git").resolve("config");
            if (!Files.isRegularFile(config)) return null;
            var inOrigin = false;
            for (var line : Files.readAllLines(config)) {
                var t = line.strip();
                if (t.startsWith("[")) inOrigin = t.equals("[remote \"origin\"]");
                else if (inOrigin && t.startsWith("url")) {
                    var m = URL.matcher(t);
                    return m.find() ? m.group(1) : null;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
