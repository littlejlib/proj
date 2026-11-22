package littlejlib.proj.track;

import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The plain-text views: current state first, one line per thing, detail only on request. */
public final class Views {

    private Views() { }

    public static final int LAST_NOTES = 5, TITLE_SHOWN = 100;

    public static void timeline(Budget b, PrpEntry prp, String uuid, PrpMeta meta, List<Note> notes, boolean all, PrpRefs prps) {
        b.line("PRP " + prp.getIndex() + "  " + prp.getName() + "  [" + prp.getStateKey() + "]  " + (uuid == null ? "" : uuid));
        if (meta != null && meta.summary() != null) b.line("summary: " + meta.summary());
        if (meta != null && meta.related() != null)
            for (var r : meta.related()) b.line("related: " + r.kind() + " " + prps.label(r.prp()) + (prps.get(r.prp()) == null ? "" : " (" + r.prp() + ")") + (r.note() == null ? "" : "  " + r.note()));
        var state = Timeline.state(notes);
        b.line("state: " + (state == null ? "none recorded" : state.kind() + " " + Now.shown(state.at()) + "  " + state.text()));
        var sessions = Timeline.sessions(notes);
        var from = all ? 0 : Timeline.recentFrom(notes, LAST_NOTES);
        if (from > 0) b.line("(" + from + " older notes, --all shows them)");
        for (var n : notes.subList(from, notes.size())) {
            var cycle = sessions.indexOf(n.session()) + 1;
            b.line("  " + Now.shown(n.at()) + "  " + pad(n.kind(), 9) + " " + n.text()
                    + (n.refs() == null ? "" : "  [" + String.join(", ", n.refs()) + "]")
                    + "  (" + (cycle > 0 ? "cycle " + cycle + ", " : "") + Session.shortId(n.session()) + ")");
        }
    }

    /** Waiting for the user first, then blocked, then open work, then the rest. */
    public static int priority(Item i) {
        return switch (i.status()) {
            case "open" -> "todo".equals(i.type()) ? 2 : 0;
            case "blocked" -> 1;
            case "doing" -> 2;
            case "hold" -> 3;
            case "done" -> 4;
            default -> 5;
        };
    }

    public static void items(Budget b, Map<Long, Item> all, List<Item> shown, PrpRefs prps) {
        var waiting = shown.stream().filter(i -> priority(i) == 0).count();
        b.line(shown.size() + " shown of " + all.size() + (waiting > 0 ? ", " + waiting + " waiting for the user" : ""));
        shown.stream().sorted(Comparator.comparingInt(Views::priority).thenComparingLong(i -> i.id())).forEach(i -> b.line(
                pad(String.valueOf(i.id()), 5) + " " + pad(i.type(), 7) + " " + pad(i.status(), 7) + " " + pad(prps.label(i.prp()), 4)
                        + " " + Budget.cut(i.title(), TITLE_SHOWN) + (i.deps() == null ? "" : "  (after " + join(i.deps()) + ")")));
    }

    public static void item(Budget b, Item i, PrpRefs prps) {
        b.line(i.id() + "  " + i.type() + "  " + i.status() + "  PRP " + prps.label(i.prp()));
        b.line("title:   " + i.title());
        if (i.detail() != null) b.line("detail:  " + i.detail());
        if (i.deps() != null) b.line("after:   " + join(i.deps()));
        if (i.refs() != null) b.line("refs:    " + String.join(", ", i.refs()));
        if (i.related() != null) b.line("related: " + String.join(", ", i.related()));
        if (i.owner() != null) b.line("owner:   " + i.owner());
        if (i.source() != null) b.line("source:  " + i.source());
        b.line("created: " + Now.shown(i.created()) + "   updated: " + Now.shown(i.updated())
                + (i.session() == null ? "" : "   by " + Session.shortId(i.session())));
    }

    static String join(List<Long> ids) {
        return String.join(", ", ids.stream().map(String::valueOf).toList());
    }

    static String pad(String s, int n) {
        var v = s == null ? "" : s;
        return v.length() >= n ? v : v + " ".repeat(n - v.length());
    }
}
