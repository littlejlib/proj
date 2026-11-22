package littlejlib.proj.track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Open work items, waiting-for-the-user first: of one PRP (recall), of one project (the start hook) and
 * of every project proj knows ({@code proj prp plan list --everywhere}).
 */
public final class Waiting {

    private Waiting() { }

    static final Set<String> LIVE = littlejlib.proj.track.Workplan.LIVE;

    /** Live items of a prp folder, owned by or related to {@code prpUuid} (null: all), in priority order. */
    public static List<Item> open(Path prpDir, String prpUuid) throws IOException {
        return Workplan.current(prpDir).values().stream()
                .filter(i -> LIVE.contains(i.status()))
                .filter(i -> prpUuid == null || prpUuid.equals(i.prp()) || (i.related() != null && i.related().contains(prpUuid)))
                .sorted(Comparator.comparingInt(Views::priority).thenComparingLong(i -> i.id()))
                .toList();
    }

    /** Open discuss and confirm items: the ones that wait for the user. */
    public static boolean forUser(Item i) {
        return Views.priority(i) == 0;
    }

    /** Every project folder with a work plan, from proj's machine-wide cache and the folder given. */
    public static Map<Path, String> projects(Path also) throws IOException {
        var out = new LinkedHashMap<Path, String>();
        if (also != null && Files.isRegularFile(Workplan.file(also.resolve("prp")))) out.put(also, null);
        for (var l : GlobalIndex.read()) {
            var p = Path.of(l.project());
            if (!out.containsKey(p) && Files.isRegularFile(Workplan.file(p.resolve("prp")))) out.put(p, l.key());
        }
        return out;
    }

    /** One line per item: project key, item id, type, status, PRP, title. */
    public static List<String> everywhere(Map<Path, String> projects, boolean onlyForUser) throws IOException {
        var rows = new ArrayList<Object[]>();
        for (var e : projects.entrySet()) {
            var prpDir = e.getKey().resolve("prp");
            var refs = PrpRefs.of(prpDir);
            var key = e.getValue() != null ? e.getValue() : e.getKey().getFileName().toString();
            for (var i : open(prpDir, null)) {
                if (onlyForUser && !forUser(i)) continue;
                rows.add(new Object[]{Views.priority(i), key + "  #" + i.id() + "  " + Views.pad(i.type(), 7) + " " + Views.pad(i.status(), 7)
                        + " PRP " + Views.pad(refs.label(i.prp()), 4) + "  " + Budget.cut(i.title(), Views.TITLE_SHOWN)});
            }
        }
        rows.sort(Comparator.comparingInt(r -> (Integer) r[0]));
        return rows.stream().map(r -> (String) r[1]).toList();
    }
}
