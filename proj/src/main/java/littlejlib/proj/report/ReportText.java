package littlejlib.proj.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The report as json, json lines (one item per line) and markdown. Markdown has no tables: it is read in plain editors. */
public final class ReportText {

    private ReportText() { }

    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static String json(ReportData d, boolean pretty) {
        return (pretty ? PRETTY : GSON).toJson(d);
    }

    public static String jsonl(ReportData d) {
        var sb = new StringBuilder();
        for (var i : d.items) sb.append(GSON.toJson(i)).append('\n');
        return sb.toString();
    }

    public static String markdown(ReportData d, List<String> panels) {
        var all = panels == null || panels.isEmpty();
        var want = all ? Set.<String>of() : Set.copyOf(panels);
        var sb = new StringBuilder();
        sb.append("# Work plan: ").append(String.join(", ", d.projects.stream().map(p -> p.name + (p.followed ? " (followed)" : "")).toList())).append("\n\n");
        sb.append("Built ").append(d.built).append(". ").append(d.items.size()).append(" items shown");
        if (d.scope.goal != null) sb.append(", goal ").append(d.scope.goal);
        if (!d.scope.prps.isEmpty()) sb.append(", PRPs ").append(String.join(", ", d.scope.prps));
        if (!d.scope.done) sb.append(", done and dropped hidden");
        sb.append(".\n");
        if (all || want.contains("waiting")) section(sb, d, "Waiting on the user", d.panels.waiting, "most unblocking first");
        if (all || want.contains("ready")) {
            section(sb, d, "Start now", d.panels.startNow, "deps done, no shared resource in the way, best first; can run together");
            var queued = new ArrayList<String>();
            for (var k : d.panels.queued) {
                var i = d.byKey.get(k);
                queued.add(k + (i.heldBy != null ? " (held by " + String.join(", ", i.heldBy) + ")" : i.queuedBehind != null ? " (after " + i.queuedBehind + ")" : ""));
            }
            if (!queued.isEmpty()) {
                sb.append("\n## Queued for a shared resource\n\n");
                for (var q : queued) sb.append("- ").append(q).append('\n');
            }
        }
        if (all || want.contains("doing")) section(sb, d, "Doing", d.panels.doing, null);
        if ((all || want.contains("path")) && d.scope.goal != null) section(sb, d, "Path to " + d.scope.goal, d.panels.path, "prerequisites first");
        if ((all || want.contains("resources")) && !d.resources.isEmpty()) {
            sb.append("\n## Shared resources\n\n");
            for (var r : d.resources) {
                sb.append("- ").append(r.name).append(r.capacity > 1 ? " (" + r.capacity + " at once)" : "").append(": ");
                sb.append(r.holders.isEmpty() ? "free" : "held by " + String.join(", ", r.holders));
                if (!r.stale.isEmpty()) sb.append("; stale claims ").append(String.join(", ", r.stale));
                if (!r.queue.isEmpty()) sb.append("; queue ").append(String.join(", ", r.queue));
                if (!r.blocked.isEmpty()) sb.append("; waiting for deps ").append(String.join(", ", r.blocked));
                sb.append('\n');
            }
        }
        if ((all || want.contains("problems")) && !d.problems.isEmpty()) {
            sb.append("\n## Problems\n\n");
            for (var p : d.problems) sb.append("- ").append(p).append('\n');
        }
        if (all || want.contains("loose")) section(sb, d, "Not linked to anything shown", d.panels.loose, null);
        return sb.toString();
    }

    static void section(StringBuilder sb, ReportData d, String title, List<String> keys, String note) {
        sb.append("\n## ").append(title).append(" (").append(keys.size()).append(")\n\n");
        if (note != null) sb.append(note).append(".\n\n");
        if (keys.isEmpty()) sb.append("Nothing.\n");
        for (var k : keys) sb.append("- ").append(line(d.byKey.get(k), k)).append('\n');
    }

    /** One item on one line: key, tag, type and status, title, then what places it. */
    public static String line(ReportData.Item i, String key) {
        if (i == null) return key + " (not shown)";
        var sb = new StringBuilder();
        sb.append(i.key).append(" [").append(i.tag).append("] ").append(i.type).append(' ').append(i.status);
        if (i.statusSince != null) sb.append(" since ").append(littlejlib.proj.track.Now.shown(i.statusSince));
        sb.append(": ").append(i.title);
        var meta = new ArrayList<String>();
        if (i.prp != null) meta.add("PRP " + (i.prp.ticket != null ? i.prp.ticket : i.prp.uuid));
        if (i.owner != null) meta.add("owner " + i.owner);
        if (i.unblocksOpen > 0) meta.add("unblocks " + i.unblocksOpen);
        if (!i.waitsForOpen.isEmpty()) meta.add("waits for " + String.join(", ", i.waitsForOpen));
        if (i.uses != null) meta.add("uses " + String.join(", ", i.uses));
        if (i.claim != null) meta.add(i.claim.live ? "claim live" : "STALE claim, " + i.claim.why);
        if (!meta.isEmpty()) sb.append(" (").append(String.join("; ", meta)).append(')');
        return sb.toString();
    }
}
