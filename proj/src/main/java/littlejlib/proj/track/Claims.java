package littlejlib.proj.track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared resources ({@code Item.uses}) and the claims on them. An item in {@code doing} holds every resource it uses.
 * A claim is live while its session's transcript was written to recently; with no transcript to look at, while the
 * item itself was touched recently. A stale claim holds nothing, and proj never changes the item because of it.
 */
public final class Claims {

    private Claims() { }

    static final Pattern RESOURCE = Pattern.compile("[^\\s*]{1,60}(\\*[1-9][0-9]?)?");

    static volatile Duration quietLimit = Duration.ofMinutes(30), untouchedLimit = Duration.ofHours(3);

    /** From settings: planClaimQuietMinutes, planClaimHours. */
    public static void configure(int quietMinutes, int untouchedHours) {
        quietLimit = Duration.ofMinutes(quietMinutes);
        untouchedLimit = Duration.ofHours(untouchedHours);
    }

    /** The resource name without its {@code *N}. */
    public static String name(String use) {
        var star = use.indexOf('*');
        return star < 0 ? use : use.substring(0, star);
    }

    /** How many may hold it at once: the N of {@code *N}, else 1. */
    public static int capacity(String use) {
        var star = use.indexOf('*');
        return star < 0 ? 1 : Integer.parseInt(use.substring(star + 1));
    }

    public record Liveness(boolean live, Instant lastActive, String why) { }

    /** Whether a doing item's claim still counts. */
    public static Liveness liveness(Item i, Instant now) {
        var t = Transcripts.lastWrite(i.session());
        if (t != null) {
            var live = Duration.between(t, now).compareTo(quietLimit) < 0;
            return new Liveness(live, t, live ? "session active" : "session quiet " + Now.age(t, now));
        }
        var touched = Now.instant(i.updated());
        var live = touched != null && Duration.between(touched, now).compareTo(untouchedLimit) < 0;
        return new Liveness(live, touched, live ? "touched recently" : "untouched " + (touched == null ? "?" : Now.age(touched, now)));
    }

    public record Holder(String project, Item item, Liveness liveness) { }

    /** Live holders of any resource {@code item} uses, over the given projects (short name to project dir), excluding the item itself. */
    public static List<String> conflicts(String project, Item item, Map<String, Path> projects) throws IOException {
        var out = new ArrayList<String>();
        if (item.uses() == null || item.uses().isEmpty()) return out;
        var now = Instant.now();
        for (var use : item.uses()) {
            var name = name(use);
            var holders = new ArrayList<String>();
            for (var e : projects.entrySet()) {
                for (var other : Workplan.current(e.getValue().resolve("prp")).values()) {
                    if (e.getKey().equals(project) && other.id().equals(item.id())) continue;
                    if (!"doing".equals(other.status()) || other.uses() == null) continue;
                    if (other.uses().stream().noneMatch(u -> name(u).equals(name))) continue;
                    var l = liveness(other, now);
                    if (l.live()) holders.add(e.getKey() + "#" + other.id() + " (doing since " + Now.shown(other.updated())
                            + ", session " + Session.shortId(other.session()) + ")");
                }
            }
            if (holders.size() >= capacity(use)) out.add(name + " is held by " + String.join(", ", holders));
        }
        return out;
    }

    /** Finds a Claude Code transcript by session id under {@code ~/.claude/projects}. */
    static final class Transcripts {
        static Instant lastWrite(String session) {
            if (session == null || session.isBlank()) return null;
            var root = Path.of(System.getProperty("user.home"), ".claude", "projects");
            try (var dirs = Files.list(root)) {
                for (var d : (Iterable<Path>) dirs::iterator) {
                    var f = d.resolve(session + ".jsonl");
                    if (Files.isRegularFile(f)) return Files.getLastModifiedTime(f).toInstant();
                }
            } catch (IOException e) {
                return null;
            }
            return null;
        }
    }
}
