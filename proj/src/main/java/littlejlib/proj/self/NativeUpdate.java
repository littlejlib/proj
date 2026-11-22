package littlejlib.proj.self;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import littlejlib.proj.util.Native;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * {@code proj update} for the native exe: reads the update file ({@code update/proj.json}: channels, releases, per-platform
 * exe with SHA-256 and urls), downloads the exe of the channel's newest release beside the running one, checks the hash, then
 * renames the running exe aside (Windows allows that, not deleting it) and puts the new one in its place. The aside copy is
 * removed by the next run of this command.
 */
public final class NativeUpdate {
    private NativeUpdate() { }

    static final String PLATFORM = "windows-x86_64";

    /** 0 when current or updated, 1 when it failed. {@code beforeSwap} stops what runs from the old exe. */
    public static int run(boolean checkOnly, Runnable beforeSwap) {
        try {
            return update(checkOnly, beforeSwap);
        } catch (IOException | RuntimeException e) {
            System.err.println("proj: update failed: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    static int update(boolean checkOnly, Runnable beforeSwap) throws IOException, InterruptedException {
        var url = NativeApp.updateUrl();
        if (url == null) throw new IOException("this exe has no update address baked in");
        var self = Native.exe();
        Files.deleteIfExists(aside(self));
        var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build();
        var file = new ObjectMapper().readTree(get(http, url));
        var latest = file.path("channels").path(NativeApp.channel()).asText(null);
        if (latest == null) throw new IOException("the update file has no release on the " + NativeApp.channel() + " channel");
        if (Versions.compare(latest, NativeApp.version()) <= 0) {
            System.out.println("proj " + NativeApp.version() + " is the newest on the " + NativeApp.channel() + " channel.");
            return 0;
        }
        System.out.println("proj " + latest + " is available (this is " + NativeApp.version() + ").");
        if (checkOnly) return 0;
        var exe = release(file, latest).path("exe").path(PLATFORM);
        if (exe.isMissingNode()) throw new IOException("release " + latest + " has no " + PLATFORM + " exe");
        var fresh = self.resolveSibling(self.getFileName() + ".new");
        download(http, exe.path("urls").path(0).asText(), fresh);
        var got = sha256(fresh);
        if (!got.equalsIgnoreCase(exe.path("sha256").asText())) {
            Files.deleteIfExists(fresh);
            throw new IOException("the download does not match its SHA-256 (got " + got + "); nothing was changed");
        }
        beforeSwap.run();
        Files.move(self, aside(self));
        Files.move(fresh, self);
        System.out.println("proj " + latest + " is in place.");
        return 0;
    }

    static JsonNode release(JsonNode file, String version) throws IOException {
        for (var r : file.path("releases")) if (version.equals(r.path("version").asText())) return r;
        throw new IOException("the update file names release " + version + " but does not list it");
    }

    static Path aside(Path self) {
        return self.resolveSibling(self.getFileName() + ".old");
    }

    static String get(HttpClient http, String url) throws IOException, InterruptedException {
        var reply = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
        if (reply.statusCode() != 200) throw new IOException(url + " answered " + reply.statusCode());
        return reply.body();
    }

    static void download(HttpClient http, String url, Path to) throws IOException, InterruptedException {
        System.err.println("downloading " + url);
        var reply = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofFile(to));
        if (reply.statusCode() != 200) {
            Files.deleteIfExists(to);
            throw new IOException(url + " answered " + reply.statusCode());
        }
    }

    static String sha256(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            var md = MessageDigest.getInstance("SHA-256");
            var buf = new byte[1 << 16];
            for (var n = in.read(buf); n > 0; n = in.read(buf)) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
