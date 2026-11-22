package littlejlib.proj.track;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** How a stored index differs from what the files say now: the stale lines, named. */
public final class IndexCheck {

    private IndexCheck() { }

    static final List<String> FIELDS = List.of("uuid", "name", "file", "state", "summary", "related", "progress",
            "notes", "openItems", "updated");

    public static List<String> stale(List<IndexLine> stored, List<IndexLine> fresh) {
        var out = new ArrayList<String>();
        var old = new LinkedHashMap<String, IndexLine>();
        for (var l : stored) old.put(l.number(), l);
        for (var now : fresh) {
            var was = old.remove(now.number());
            if (was == null) {
                out.add(now.number() + ": missing from the index  " + now.name());
                continue;
            }
            var changed = FIELDS.stream().filter(f -> !Objects.equals(get(was, f), get(now, f))).toList();
            if (!changed.isEmpty()) out.add(now.number() + ": stale  " + String.join(", ", changed));
        }
        for (var gone : old.values()) out.add(gone.number() + ": in the index but no longer a PRP  " + gone.name());
        return out;
    }

    public static List<String> withoutUuid(List<IndexLine> fresh) {
        return fresh.stream().filter(l -> l.uuid() == null || l.uuid().isEmpty()).map(IndexLine::number).toList();
    }

    static Object get(IndexLine l, String field) {
        Function<IndexLine, Object> f = switch (field) {
            case "uuid" -> IndexLine::uuid;
            case "name" -> IndexLine::name;
            case "file" -> IndexLine::file;
            case "state" -> IndexLine::state;
            case "summary" -> IndexLine::summary;
            case "related" -> x -> x.related() == null ? null : Jsonl.GSON.toJson(x.related());
            case "progress" -> x -> x.progress() + "@" + x.progressAt();
            case "notes" -> IndexLine::notes;
            case "openItems" -> IndexLine::openItems;
            default -> IndexLine::updated;
        };
        return f.apply(l);
    }
}
