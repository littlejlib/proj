package littlejlib.proj.track;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * An older work-plan jsonl (ids like {@code "0001"}, rows changed in place) into {@code workplan.jsonl}.
 * Ids become plain numbers, every item gets the owning PRP's uuid unless it names one. Refuses to mix
 * into a work plan that already has items, so ids never collide.
 */
public final class WorkplanImport {

    private WorkplanImport() { }

    public static List<Item> run(Path from, Path prpDir, String prpUuid, boolean dryRun) throws IOException {
        var f = Workplan.file(prpDir);
        return Jsonl.locked(f, () -> {
            if (!Workplan.current(prpDir).isEmpty()) throw new IllegalStateException(f + " already has items; import only into an empty work plan");
            var items = new ArrayList<Item>();
            for (var line : Files.readAllLines(from, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                items.add(item(JsonParser.parseString(line).getAsJsonObject(), prpUuid));
            }
            var known = Workplan.fold(items);
            for (var i : items) Workplan.validate(i, known, false);
            var over = items.stream().filter(i -> i.title().length() > Workplan.MAX_TITLE || (i.detail() != null && i.detail().length() > Workplan.MAX_DETAIL)).count();
            if (over > 0) System.err.println(over + " items are over the title or detail limit; kept as they are");
            if (!dryRun) for (var i : items) Jsonl.write(f, i);
            return items;
        });
    }

    static Item item(JsonObject o, String prpUuid) {
        var i = new Item().id(number(o.get("id"))).type(text(o, "type")).status(text(o, "status"))
                .title(text(o, "title")).detail(text(o, "detail")).owner(joined(o.get("owner")))
                .source(joined(o.get("source"))).created(text(o, "created")).updated(text(o, "updated"));
        i.prp(o.has("prp") ? text(o, "prp") : prpUuid);
        var deps = new ArrayList<Long>();
        for (var d : list(o.get("deps"))) deps.add(number(d));
        i.deps(deps.isEmpty() ? null : deps);
        var refs = new ArrayList<String>();
        for (var r : list(o.get("refs"))) refs.add(r.getAsString().matches("0+\\d+") ? String.valueOf(number(r)) : r.getAsString());
        i.refs(refs.isEmpty() ? null : refs);
        return i;
    }

    static Long number(JsonElement e) {
        if (e == null || e.isJsonNull()) throw new IllegalArgumentException("an item without an id");
        return Long.parseLong(e.getAsString().strip());
    }

    static String text(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    static String joined(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (!e.isJsonArray()) return e.getAsString();
        return String.join("; ", list(e).stream().map(JsonElement::getAsString).toList());
    }

    static List<JsonElement> list(JsonElement e) {
        var out = new ArrayList<JsonElement>();
        if (e == null || e.isJsonNull()) return out;
        if (e instanceof JsonArray a) a.forEach(out::add);
        else out.add(e);
        return out;
    }
}
