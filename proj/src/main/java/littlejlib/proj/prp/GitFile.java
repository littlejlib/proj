package littlejlib.proj.prp;

import java.nio.file.Path;
import java.util.regex.Pattern;

/** A file in a git repository: where to clone it from, at which ref (blank = the default branch), and the path inside. */
public record GitFile(String remote, String ref, String path) {

    static final Pattern HOST = Pattern.compile("(?i)(?:git@|ssh://(?:[^@/]+@)?|git://|file://)([^:/]*)[:/](.*)");

    /** Null when the path is empty or climbs out of the clone. */
    static GitFile of(String remote, String ref, String path) {
        var p = path.replace('\\', '/').replaceFirst("^/+", "");
        if (p.isBlank() || remote.isBlank() || p.matches("(.*/)?\\.\\.(/.*)?")) return null;
        return new GitFile(remote.strip(), ref.strip(), p);
    }

    /** {@code githubcache/<org>/<repo>/}; another host goes under {@code _git/<host>/}, a local path under {@code _git/_local/}. */
    public Path cloneDir() {
        var m = HOST.matcher(remote);
        var host = m.matches() ? m.group(1) : "";
        var tail = (m.matches() ? m.group(2) : remote).replaceAll("\\.git/*$", "").replace('\\', '/');
        var parts = tail.split("/");
        var repo = safe(parts[parts.length - 1]);
        var org = parts.length > 1 ? safe(parts[parts.length - 2]) : "_none";
        var root = GithubCache.root();
        return "github.com".equalsIgnoreCase(host) ? root.resolve(org).resolve(repo)
                : root.resolve("_git").resolve(host.isBlank() ? "_local" : safe(host)).resolve(org).resolve(repo);
    }

    public Path file() {
        return cloneDir().resolve(path);
    }

    /** The directory to check out sparsely, empty for a file at the repository root. */
    public String dir() {
        var cut = path.lastIndexOf('/');
        return cut < 0 ? "" : path.substring(0, cut);
    }

    static String safe(String s) {
        var t = s.replaceAll("[:*\"<>|?]", "_");
        return t.isBlank() || t.equals("..") || t.equals(".") ? "_" : t;
    }

    /** Names the stamp that records when this ref and path were last fetched. */
    public String stampName() {
        return "proj-fetched-" + Integer.toHexString((ref + ":" + path).hashCode());
    }
}
