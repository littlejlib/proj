package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A file of a private repository kept in a shallow, sparse clone under {@code githubcache/<org>/<repo>/},
 * fetched with plain {@code git} (so the user's SSH setup does the authentication). Refreshed when older than
 * a day or on request; when git fails the last copy is used.
 */
public final class GitCache {

    private GitCache() { }

    /** {@code warn} is set when git failed and the copy on disk was used instead. */
    public record Copy(String text, Path file, long fetchedAt, boolean fresh, String warn) { }

    static final long MAX_AGE_MS = 24 * 3600_000L;

    /** Per git command; an SSH approval window may take a while to be answered. */
    public static long timeoutSeconds = 120;

    public static Copy get(GitFile f, boolean refresh) throws IOException {
        var file = f.file();
        var stamp = f.cloneDir().resolve(".git").resolve(f.stampName());
        var age = Files.isRegularFile(stamp) && Files.isRegularFile(file) ? System.currentTimeMillis() - Files.getLastModifiedTime(stamp).toMillis() : Long.MAX_VALUE;
        if (!refresh && age < MAX_AGE_MS) return copy(file, stamp, false, null);
        try {
            update(f);
            Files.writeString(stamp, "fetched\n", StandardCharsets.UTF_8);
            Files.setLastModifiedTime(stamp, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            if (!Files.isRegularFile(file)) throw new IOException(f.path() + " is not in the repository at " + (f.ref().isBlank() ? "the default branch" : f.ref()));
            return copy(file, stamp, true, null);
        } catch (IOException e) {
            if (Files.isRegularFile(file)) return copy(file, stamp, false, "git failed (" + e.getMessage() + "), so the earlier local copy is used");
            throw e;
        }
    }

    static Copy copy(Path file, Path stamp, boolean fresh, String warn) throws IOException {
        var at = Files.isRegularFile(stamp) ? Files.getLastModifiedTime(stamp).toMillis() : Files.getLastModifiedTime(file).toMillis();
        return new Copy(Files.readString(file, StandardCharsets.UTF_8), file, at, fresh, warn);
    }

    static void update(GitFile f) throws IOException {
        var dir = f.cloneDir();
        if (!Files.isDirectory(dir.resolve(".git"))) {
            Files.createDirectories(dir.getParent());
            git(dir.getParent(), "clone", "--depth", "1", "--filter=blob:none", "--sparse", f.remote(), dir.getFileName().toString());
        }
        if (!f.dir().isEmpty()) git(dir, "sparse-checkout", "add", f.dir());
        git(dir, "fetch", "--depth", "1", "origin", f.ref().isBlank() ? "HEAD" : f.ref());
        git(dir, "checkout", "-q", "-f", "FETCH_HEAD");
    }

    static void git(Path dir, String... args) throws IOException {
        var cmd = new ArrayList<String>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        var log = Files.createTempFile("projgit", ".log");
        try {
            var pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            var p = pb.start();
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                throw new IOException("git " + args[0] + " timed out after " + timeoutSeconds + " s");
            }
            if (p.exitValue() != 0) throw new IOException("git " + args[0] + " failed: " + tail(Files.readString(log, StandardCharsets.UTF_8)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted running git " + args[0]);
        } finally {
            Files.deleteIfExists(log);
        }
    }

    /** The last words of git's output on one line, with any user:password in a url removed. */
    static String tail(String out) {
        var t = out.replaceAll("(?i)(://)[^/@\\s]*@", "$1").replaceAll("\\s+", " ").strip();
        return t.length() > 300 ? t.substring(t.length() - 300) : t;
    }
}
