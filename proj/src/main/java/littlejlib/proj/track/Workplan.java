package littlejlib.proj.track;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * {@code prp/workplan.jsonl}: the project's work items with one plain counter (1, 2, 3 .. of any width).
 * Every change appends the item's whole new state; the last line of an id wins and the earlier ones are
 * its history. Taking an id and writing it happen under one lock, so two sessions never get the same id.
 */
public final class Workplan {

    private Workplan() { }

    public static final List<String> TYPES = List.of("todo", "discuss", "confirm"),
            STATUSES = List.of("open", "doing", "done", "dropped", "hold", "blocked");

    /** Statuses still in play; {@code doing} holds the resources the item {@code uses}. */
    public static final java.util.Set<String> LIVE = java.util.Set.of("open", "doing", "blocked", "hold");

    public static final List<String> TAGS = List.of("critical", "infra", "extra");

    public static final int MAX_TITLE = 100, MAX_DETAIL = 1000;

    static final java.util.regex.Pattern OLD_TAG = java.util.regex.Pattern.compile("^\\s*\\[(critical|infra|extra)\\]", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** The item's importance: the field, else the "[critical]" prefix items carried before the field existed, else infra. */
    public static String tagOf(Item i) {
        if (i.tag() != null) return i.tag();
        var m = i.detail() == null ? null : OLD_TAG.matcher(i.detail());
        return m != null && m.find() ? m.group(1).toLowerCase() : "infra";
    }

    public static Path file(Path prpDir) {
        return prpDir.resolve("workplan.jsonl");
    }

    public static List<Item> lines(Path prpDir) throws IOException {
        return Jsonl.read(file(prpDir), Item.class);
    }

    /** Current state of every item, in order of first appearance. */
    public static Map<Long, Item> fold(List<Item> lines) {
        var out = new LinkedHashMap<Long, Item>();
        for (var i : lines) if (i.id() != null) out.put(i.id(), i);
        return out;
    }

    public static Map<Long, Item> current(Path prpDir) throws IOException {
        return fold(lines(prpDir));
    }

    public static Item add(Path prpDir, Item draft) throws IOException {
        var f = file(prpDir);
        return Jsonl.locked(f, () -> {
            var items = current(prpDir);
            var now = Now.iso();
            draft.id(items.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1)
                    .status(draft.status() == null ? "open" : draft.status()).created(now).updated(now);
            validate(draft, items);
            Jsonl.write(f, draft);
            return draft;
        });
    }

    /** A last check on a change, run under the lock with the old and new state; throws to refuse it. */
    public interface Guard {
        void check(Path prpDir, Item was, Item next) throws IOException;
    }

    public static Item set(Path prpDir, long id, Consumer<Item> change, String session) throws IOException {
        return set(prpDir, id, change, session, null);
    }

    public static Item set(Path prpDir, long id, Consumer<Item> change, String session, Guard guard) throws IOException {
        var f = file(prpDir);
        return Jsonl.locked(f, () -> {
            var items = current(prpDir);
            var was = items.get(id);
            if (was == null) throw new IllegalArgumentException("no item " + id + " in " + f);
            var next = Jsonl.copy(was, Item.class);
            change.accept(next);
            next.id(id).updated(Now.iso()).session(session);
            validate(next, items, !Objects.equals(was.title(), next.title()) || !Objects.equals(was.detail(), next.detail()));
            if (guard != null) guard.check(prpDir, was, next);
            Jsonl.write(f, next);
            return next;
        });
    }

    public static List<Item> history(Path prpDir, long id) throws IOException {
        return lines(prpDir).stream().filter(i -> i.id() != null && i.id() == id).toList();
    }

    static void validate(Item i, Map<Long, Item> items) {
        validate(i, items, true);
    }

    /** {@code limits} false checks structure only: for importing data written before the limits existed. */
    static void validate(Item i, Map<Long, Item> items, boolean limits) {
        need(TYPES.contains(i.type()), "type must be one of " + String.join(", ", TYPES));
        need(STATUSES.contains(i.status()), "status must be one of " + String.join(", ", STATUSES));
        need(i.tag() == null || TAGS.contains(i.tag()), "tag must be one of " + String.join(", ", TAGS));
        need(i.title() != null && !i.title().isBlank(), "an item needs a title");
        need(!limits || i.title().length() <= MAX_TITLE, "the title is " + i.title().length() + " characters, the limit is " + MAX_TITLE);
        need(!limits || i.detail() == null || i.detail().length() <= MAX_DETAIL, "the detail is over " + MAX_DETAIL
                + " characters: write it into a PRP sub-file and name it with --ref");
        if (i.uses() != null) {
            need(i.uses().size() <= 5, "at most 5 shared resources per item");
            for (var u : i.uses()) need(Claims.RESOURCE.matcher(u).matches(),
                    "a shared resource is a name without spaces, at most 60 characters, optionally *N for N at once: " + u);
        }
        if (i.deps() != null) for (var d : i.deps()) {
            need(!d.equals(i.id()), "item " + d + " cannot depend on itself");
            need(items.containsKey(d), "no item " + d + " to depend on");
        }
    }

    static void need(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}
