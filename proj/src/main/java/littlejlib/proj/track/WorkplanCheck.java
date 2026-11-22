package littlejlib.proj.track;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What in a work plan has gone wrong or gone stale. Flags only; nothing is changed. */
public final class WorkplanCheck {

    private WorkplanCheck() { }

    public static final int STALE_DAYS = 30;

    static final Set<String> LIVE = littlejlib.proj.track.Workplan.LIVE;

    public static List<String> problems(Map<Long, Item> items, PrpRefs prps) {
        var out = new ArrayList<String>();
        for (var i : items.values()) {
            var live = LIVE.contains(i.status());
            if (i.deps() != null) for (var d : i.deps()) {
                var dep = items.get(d);
                if (dep == null) out.add(i.id() + ": depends on " + d + ", which does not exist");
                else if (live && "dropped".equals(dep.status())) out.add(i.id() + ": depends on " + d + ", which was dropped");
            }
            if (i.prp() != null && prps.get(i.prp()) == null) out.add(i.id() + ": its PRP " + i.prp() + " is not in this project");
            if (live && prps.closed(i.prp())) out.add(i.id() + ": still " + i.status() + " but PRP " + prps.label(i.prp()) + " is closed");
            if (live && Now.ageDays(i.updated()) > STALE_DAYS)
                out.add(i.id() + ": stale, " + i.status() + " and untouched for " + Now.ageDays(i.updated()) + " days");
        }
        for (var cycle : cycles(items)) out.add("dependency cycle: " + cycle);
        return out;
    }

    static List<String> cycles(Map<Long, Item> items) {
        var out = new ArrayList<String>();
        var done = new HashSet<Long>();
        for (var start : items.keySet()) walk(start, items, new ArrayList<>(), done, out);
        return out;
    }

    static void walk(Long id, Map<Long, Item> items, List<Long> path, Set<Long> done, List<String> out) {
        if (done.contains(id)) return;
        var at = path.indexOf(id);
        if (at >= 0) {
            var loop = new ArrayList<>(path.subList(at, path.size()));
            loop.add(id);
            out.add(String.join(" -> ", loop.stream().map(String::valueOf).toList()));
            return;
        }
        var item = items.get(id);
        if (item == null || item.deps() == null) {
            done.add(id);
            return;
        }
        path.add(id);
        for (var d : item.deps()) walk(d, items, path, done, out);
        path.remove(path.size() - 1);
        done.add(id);
    }
}
