package littlejlib.proj.prp;

import littlejlib.proj.util.AppPaths;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * A project's icon as a local square PNG, which is all Tabs ever reads.
 * <p>
 * Resolution: the {@code icon} in proj.json, else the avatar of the GitHub organisation the folder's
 * {@code origin} belongs to. A local file named by {@code icon} is read where it is, so nothing is
 * copied; a URL, or the organisation avatar, is fetched once into {@code ~/littlejlib/proj/icons}
 * as {@code <uuid>.png}. A failed fetch leaves a {@code .none} marker so it is retried tomorrow
 * rather than on every launch.
 */
public final class ProjIcon {

    private ProjIcon() { }

    static final int SIZE = 64;
    static final Duration RETRY = Duration.ofDays(1), TIMEOUT = Duration.ofSeconds(10);

    public static Path cache(String uuid) {
        return AppPaths.home().resolve("icons").resolve(uuid + ".png");
    }

    static Path marker(String uuid) {
        return AppPaths.home().resolve("icons").resolve(uuid + ".none");
    }

    /** The file to show for this project now, or null. Does no network. */
    public static Path current(Path project, ProjJson p) {
        var local = local(project, p);
        if (local != null) return local;
        return p.uuid() != null && Files.isRegularFile(cache(p.uuid())) ? cache(p.uuid()) : null;
    }

    static Path local(Path project, ProjJson p) {
        var icon = p.icon();
        if (icon == null || icon.isBlank() || isUrl(icon)) return null;
        var f = project.resolve(icon);
        return Files.isRegularFile(f) ? f : null;
    }

    static boolean isUrl(String s) {
        return s.startsWith("http://") || s.startsWith("https://");
    }

    /** Fetches what is missing. {@code force} drops the cached copy first, after an edit. */
    public static void refresh(Path project, ProjJson p, boolean force) {
        try {
            if (p.uuid() == null || local(project, p) != null) return;
            if (force) {
                Files.deleteIfExists(cache(p.uuid()));
                Files.deleteIfExists(marker(p.uuid()));
            }
            if (Files.exists(cache(p.uuid())) || recentlyFailed(p.uuid())) return;
            var url = p.icon() != null && isUrl(p.icon()) ? p.icon() : orgAvatar(project);
            if (url == null || !fetch(url, cache(p.uuid()))) touch(marker(p.uuid()));
        } catch (IOException | RuntimeException e) {
            System.err.println("[ProjIcon] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static String orgAvatar(Path project) {
        var org = GitOrigin.org(project);
        return org == null ? null : "https://github.com/" + org + ".png?size=" + SIZE;
    }

    static boolean recentlyFailed(String uuid) throws IOException {
        var m = marker(uuid);
        return Files.exists(m)
                && Files.getLastModifiedTime(m).toInstant().isAfter(java.time.Instant.now().minus(RETRY));
    }

    static void touch(Path marker) throws IOException {
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "");
    }

    static boolean fetch(String url, Path to) throws IOException {
        try {
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(TIMEOUT).build();
            var reply = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (reply.statusCode() != 200 || reply.body().length > (4 << 20)) return false;
            var image = ImageIO.read(new java.io.ByteArrayInputStream(reply.body()));
            if (image == null) return false;
            Files.createDirectories(to.getParent());
            ImageIO.write(square(image), "png", to.toFile());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.net.http.HttpTimeoutException | java.net.ConnectException e) {
            System.err.println("[ProjIcon] " + e.getClass().getSimpleName() + ": " + url);
            return false;
        }
    }

    /** Fitted into a SIZE square, centred, never stretched. */
    static BufferedImage square(BufferedImage in) {
        var out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        var scale = Math.min((double) SIZE / in.getWidth(), (double) SIZE / in.getHeight());
        var w = (int) Math.round(in.getWidth() * scale);
        var h = (int) Math.round(in.getHeight() * scale);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(in, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
        g.dispose();
        return out;
    }
}
