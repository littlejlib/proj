package littlejlib.proj.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pulls the committer name and the origin remote out of .git/config, onto the record.
 * <p>
 * The url is handled by stripping the scheme and any trailing .git and then taking the host and the
 * last two path segments, so ssh ({@code git@github.com:org/repo.git}) and https
 * ({@code https://github.com/org/repo}) both resolve. mm's single regex required a colon and a
 * .git suffix, which made every https remote report an org of "//github.com/org".
 * <p>
 * The file is read section by section rather than by scanning for the first {@code url =} line
 * anywhere in it. A config with several remotes, or with an {@code [lfs]} section, carries more
 * than one url, and the first one in the file is not reliably origin - which would have quietly
 * attributed a project to whichever remote happened to be declared first.
 */
public final class GitConfigReader {

    private GitConfigReader() { }

    private static final Pattern
            SECTION = Pattern.compile("^\\s*\\[(.+?)]\\s*$"),
            KEY_VALUE = Pattern.compile("^\\s*([A-Za-z0-9_-]+)\\s*=\\s*(.*?)\\s*$"),
            REMOTE_SECTION = Pattern.compile("^remote\\s+\"(.+)\"$");

    public static void applyTo(ProjectRecord rec, Path gitDir) {
        if (rec == null || gitDir == null || !Files.isDirectory(gitDir)) return;

        var config = gitDir.resolve("config");
        if (!Files.isRegularFile(config)) return;

        String text;
        try {
            text = Files.readString(config);
        } catch (Exception e) {
            return;
        }

        var remotes = new LinkedHashMap<String, String>();
        String section = "", user = null;

        for (var line : text.split("\\R")) {
            var head = SECTION.matcher(line);
            if (head.matches()) {
                section = head.group(1).trim();
                continue;
            }
            var kv = KEY_VALUE.matcher(line);
            if (!kv.matches()) continue;

            var key = kv.group(1);
            var value = kv.group(2);

            if (section.equals("user") && key.equalsIgnoreCase("name")) user = value;

            var remote = REMOTE_SECTION.matcher(section);
            if (remote.matches() && key.equalsIgnoreCase("url")) {
                remotes.putIfAbsent(remote.group(1), value);
            }
        }

        if (user != null && !user.isBlank()) rec.gitUser(user);
        applyUrl(rec, originOf(remotes));
    }

    /** origin when it exists, otherwise the first remote declared - and null when there is none. */
    static String originOf(Map<String, String> remotes) {
        var origin = remotes.get("origin");
        if (origin != null && !origin.isBlank()) return origin;
        return remotes.values().stream().filter(u -> u != null && !u.isBlank()).findFirst().orElse(null);
    }

    static void applyUrl(ProjectRecord rec, String raw) {
        if (raw == null || raw.isBlank()) return;
        rec.gitRemote(raw.trim());

        var u = raw.trim();
        if (u.endsWith(".git")) u = u.substring(0, u.length() - 4);
        u = u.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        var at = u.indexOf('@');
        if (at >= 0) u = u.substring(at + 1);
        u = u.replace(':', '/');

        var parts = new java.util.ArrayList<>(java.util.List.of(u.split("/")));
        parts.removeIf(String::isBlank);

        // The host is whatever stands before the path; a remote that is a local directory has no
        // host at all, and saying so is more useful than inventing one out of a drive letter.
        if (!parts.isEmpty() && parts.get(0).contains(".")) {
            rec.gitHost(parts.remove(0).toLowerCase());
        }
        if (parts.size() >= 2) {
            rec.gitOrg(parts.get(parts.size() - 2)).gitRepo(parts.get(parts.size() - 1));
        }
    }
}
