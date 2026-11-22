package littlejlib.proj.prp;

import littlejlib.proj.util.AppPaths;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * An online text file kept in {@code ~/littlejlib/proj/githubcache/<org>/<repo>/<path>}. A GitHub page
 * url ({@code github.com/o/r/blob/<ref>/p}) is read from its raw form. The copy is refreshed when older
 * than a day; without a network the last copy is used.
 */
public final class GithubCache {

    private GithubCache() { }

    public record Copy(String text, Path file, long fetchedAt, boolean fresh) { }

    static final Pattern BLOB = Pattern.compile("https?://github\\.com/([^/]+)/([^/]+)/(?:blob|raw)/([^/]+)/(.+)");

    static final Pattern RAW = Pattern.compile("https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)");

    static final long MAX_AGE_MS = 24 * 3600_000L;

    public static Path root() {
        return AppPaths.home().resolve("githubcache");
    }

    /** Where a url is kept; other hosts go under {@code _web/<host>/<path>}. */
    public static Path fileFor(String url) {
        for (var p : new Pattern[]{BLOB, RAW}) {
            var m = p.matcher(url);
            if (m.matches()) return root().resolve(m.group(1)).resolve(m.group(2)).resolve(safe(m.group(4)));
        }
        var u = URI.create(url);
        return root().resolve("_web").resolve(u.getHost()).resolve(safe(u.getPath().replaceFirst("^/", "")));
    }

    static String rawUrl(String url) {
        var m = BLOB.matcher(url);
        return m.matches() ? "https://raw.githubusercontent.com/" + m.group(1) + "/" + m.group(2) + "/" + m.group(3) + "/" + m.group(4) : url;
    }

    static String safe(String path) {
        var p = path.replaceAll("[?#].*$", "").replaceAll("[:*\"<>|]", "_");
        return p.isBlank() ? "index" : p;
    }

    public static Copy get(String url, boolean refresh) throws IOException {
        var file = fileFor(url);
        var age = Files.exists(file) ? System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() : Long.MAX_VALUE;
        if (!refresh && age < MAX_AGE_MS) return new Copy(Files.readString(file, StandardCharsets.UTF_8), file, Files.getLastModifiedTime(file).toMillis(), false);
        try {
            var text = fetch(rawUrl(url));
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return new Copy(text, file, System.currentTimeMillis(), true);
        } catch (IOException e) {
            if (Files.exists(file)) return new Copy(Files.readString(file, StandardCharsets.UTF_8), file, Files.getLastModifiedTime(file).toMillis(), false);
            throw e;
        }
    }

    static String fetch(String url) throws IOException {
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
            var res = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + " for " + url);
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted fetching " + url);
        }
    }
}
