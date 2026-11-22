package littlejlib.proj.report;

import littlejlib.proj.prp.PrpNumber;
import littlejlib.proj.track.GlobalFind;
import littlejlib.proj.track.GlobalIndex;
import littlejlib.proj.track.Now;
import littlejlib.proj.track.ProjectScope;
import littlejlib.proj.track.WorkGraph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds {@link ReportData} from the files: no AI, no state of its own beyond what a caller caches. */
public final class ReportBuilder {

    private ReportBuilder() { }

    public record Edge(String from, String to, boolean cross) { }

    /** {@code here}: the project to report on when the query names none. */
    public static ReportData build(ReportQuery q, Path here, WorkGraph.PrpSource source) throws IOException {
        var data = new ReportData();
        data.built = Now.iso();
        data.scope.asked.addAll(q.with());
        data.scope.tags.addAll(q.tags());
        data.scope.done = q.done();

        var projects = new ArrayList<ProjectScope.Project>();
        if (q.with().isEmpty()) {
            if (here == null) throw new IllegalArgumentException("name the projects: with=<name, uuid, alias or path>[,...]");
            projects.add(ProjectScope.resolve(here.toString()));
        } else for (var t : q.with()) add(projects, ProjectScope.resolve(t));
        var followed = new HashSet<String>();
        if (q.follow()) {
            for (int round = 0; round < 5; round++) {
                var missing = WorkGraph.pointedAt(ProjectScope.dirs(projects));
                if (missing.isEmpty()) break;
                var grew = false;
                for (var entry : missing) {
                    var tokens = entry.split(" ", 2);          // "uuid name" or "name"
                    ProjectScope.Project p = null;
                    for (var t : tokens) {
                        try {
                            p = ProjectScope.resolve(t);
                            break;
                        } catch (IllegalArgumentException ignored) {
                            // try the name next
                        }
                    }
                    if (p == null) {
                        data.problems.add("items point at project " + (tokens.length == 2 ? tokens[1] + " (uuid " + tokens[0] + ")" : entry) + ", which proj does not know");
                        continue;
                    }
                    if (add(projects, p)) {
                        followed.add(p.name());
                        grew = true;
                    }
                }
                if (!grew) break;
            }
        }
        data.scope.followed.addAll(followed);

        var g = WorkGraph.load(ProjectScope.dirs(projects), source);
        for (var p : projects) {
            var dp = new ReportData.Project();
            dp.name = p.name();
            dp.uuid = p.uuid();
            dp.dir = p.dir().toString();
            dp.followed = followed.contains(p.name());
            for (var n : g.nodes().values()) if (n.project().equals(p.name())) {
                dp.items++;
                if (!n.done()) dp.open++;
            }
            data.projects.add(dp);
        }
        var uuidOf = new LinkedHashMap<String, String>();
        for (var p : projects) uuidOf.put(p.name(), p.uuid());

        // PRP filter: tickets, uuids, was:<old>
        var prpUuids = new LinkedHashSet<String>();
        for (var t : q.prps()) {
            var hits = GlobalFind.find(GlobalIndex.read(), t);
            if (hits.size() != 1) throw new IllegalArgumentException("prp " + t + " matches " + hits.size() + " PRPs; use its ticket or uuid");
            prpUuids.add(hits.get(0).uuid());
            data.scope.prps.add(t);
        }

        // goal
        String goal = null;
        if (q.goal() != null) {
            var ref = ReportQuery.itemRef(q.goal());
            if (ref == null) throw new IllegalArgumentException("goal is Project:id, e.g. billing:31");
            var p = ProjectScope.resolve(ref[0]);
            goal = p.name() + "#" + ref[1];
            if (!g.nodes().containsKey(goal)) data.problems.add("goal " + goal + " is not among the items of " + String.join(", ", uuidOf.keySet()));
        }
        data.scope.goal = goal;
        var pathNodes = goal == null ? List.<WorkGraph.Node>of() : g.pathTo(goal);
        var onPath = new HashSet<String>();
        pathNodes.forEach(n -> onPath.add(n.key()));

        // what is shown
        var shown = new LinkedHashSet<String>();
        for (var n : g.nodes().values()) {
            if (!q.done() && n.done()) continue;
            if (!q.tags().isEmpty() && !q.tags().contains(n.tag())) continue;
            if (!prpUuids.isEmpty() && !prpUuids.contains(n.item().prp())
                    && (n.item().related() == null || n.item().related().stream().noneMatch(prpUuids::contains))) continue;
            shown.add(n.key());
        }

        var start = g.start();
        var ready = new LinkedHashSet<String>();
        g.readyNow().forEach(n -> ready.add(n.key()));

        // tickets: the number shortened to the width each project needs
        var width = new LinkedHashMap<String, Integer>();
        for (var e : g.prps().entrySet()) {
            var highest = e.getValue().values().stream().map(WorkGraph.Prp::number).filter(n -> n != null && !PrpNumber.isLegacy(n))
                    .max(String::compareTo).orElse("0001");
            width.put(e.getKey(), PrpNumber.width(highest));
        }

        for (var n : g.nodes().values()) {
            if (!shown.contains(n.key())) continue;
            var i = n.item();
            var it = new ReportData.Item();
            it.key = n.key();
            it.project = n.project();
            it.projectUuid = uuidOf.get(n.project());
            it.id = i.id();
            if (i.prp() != null) {
                it.prp = new ReportData.PrpRef();
                it.prp.uuid = i.prp();
                if (n.prp() != null) {
                    it.prp.number = n.prp().number();
                    it.prp.name = n.prp().name();
                    it.prp.closed = n.prp().closed();
                    it.prp.ticket = n.project() + "-" + shortForm(n.prp().number(), width.get(n.project()));
                }
            }
            it.type = i.type();
            it.status = i.status();
            it.statusSince = g.statusSince(n.key());
            it.tag = n.tag();
            it.owner = i.owner();
            it.title = i.title();
            it.detail = i.detail();
            it.uses = i.uses();
            it.waitsFor.addAll(n.ins());
            n.ins().stream().filter(k -> !g.nodes().get(k).done()).forEach(it.waitsForOpen::add);
            it.unblocks.addAll(n.outs());
            it.unblocksOpen = g.unblocks(n.key());
            it.score = g.score(n.key());
            it.ready = ready.contains(n.key());
            it.startNow = start.startNow().contains(n.key());
            it.queuedBehind = start.queuedBehind().get(n.key());
            it.heldBy = start.heldBy().get(n.key());
            it.forUser = n.forUser();
            it.onGoalPath = onPath.contains(n.key());
            it.doing = "doing".equals(i.status());
            it.done = n.done();
            var c = g.claim(n.key());
            if (c != null) {
                it.claim = new ReportData.Claim();
                it.claim.session = i.session();
                it.claim.since = it.statusSince;
                it.claim.live = c.live();
                it.claim.lastActive = c.lastActive() == null ? null : c.lastActive().toString();
                it.claim.why = c.why();
            }
            it.refs = i.refs();
            it.created = i.created();
            it.updated = i.updated();
            it.session = i.session();
            it.source = i.source();
            data.items.add(it);
            data.byKey.put(it.key, it);
        }

        // panels, over shown items
        g.waitingOnUser().stream().map(WorkGraph.Node::key).filter(shown::contains).forEach(data.panels.waiting::add);
        ready.stream().filter(shown::contains).forEach(data.panels.ready::add);
        start.startNow().stream().filter(shown::contains).forEach(data.panels.startNow::add);
        ready.stream().filter(k -> shown.contains(k) && !start.startNow().contains(k)).forEach(data.panels.queued::add);
        g.nodes().values().stream().filter(n -> "doing".equals(n.item().status()) && shown.contains(n.key())).forEach(n -> data.panels.doing.add(n.key()));
        pathNodes.stream().map(WorkGraph.Node::key).filter(shown::contains).forEach(data.panels.path::add);
        var layout = g.layout(shown);
        data.columns = layout.columns();
        data.panels.loose.addAll(layout.loose());
        for (var e : g.edges()) if (shown.contains(e.from()) && shown.contains(e.to())) data.edges.add(new Edge(e.from(), e.to(), e.cross()));

        for (var r : g.resources()) {
            var dr = new ReportData.Resource();
            dr.name = r.name();
            dr.capacity = r.capacity();
            dr.holders = r.holders();
            dr.stale = r.stale();
            dr.queue = r.queue();
            dr.blocked = r.blocked();
            data.resources.add(dr);
        }

        // PRPs that have items in view
        var prpRows = new LinkedHashMap<String, ReportData.Prp>();
        for (var it : data.items) {
            if (it.prp == null) continue;
            var row = prpRows.computeIfAbsent(it.prp.uuid, u -> {
                var r = new ReportData.Prp();
                r.uuid = u;
                r.project = it.project;
                r.ticket = it.prp.ticket;
                r.number = it.prp.number;
                r.name = it.prp.name;
                r.closed = it.prp.closed;
                return r;
            });
            if (it.done) row.done++;
            else row.open++;
            if (row.lastItemUpdate == null || (it.updated != null && it.updated.compareTo(row.lastItemUpdate) > 0)) row.lastItemUpdate = it.updated;
        }
        data.prps.addAll(prpRows.values());

        problems(g, data);
        return data;
    }

    static boolean add(List<ProjectScope.Project> list, ProjectScope.Project p) {
        if (list.stream().anyMatch(x -> x.dir().equals(p.dir()))) return false;
        list.add(p);
        return true;
    }

    static String shortForm(String number, Integer width) {
        try {
            return number == null || PrpNumber.isLegacy(number) ? number : PrpNumber.shortForm(number, width == null ? 4 : width);
        } catch (RuntimeException e) {
            return number;
        }
    }

    static void problems(WorkGraph g, ReportData data) {
        var live = littlejlib.proj.track.Workplan.LIVE;
        for (var n : g.nodes().values()) {
            var i = n.item();
            if (i.deps() != null) for (var d : i.deps())
                if (!g.nodes().containsKey(n.project() + "#" + d)) data.problems.add(n.key() + ": depends on " + d + ", which does not exist");
            if (i.xdeps() != null) for (var x : i.xdeps())
                if (n.ins().stream().noneMatch(k -> k.endsWith("#" + x.id()) && g.nodes().get(k).project().equalsIgnoreCase(String.valueOf(x.project())))
                        && g.prps().keySet().stream().anyMatch(p -> p.equalsIgnoreCase(String.valueOf(x.project()))))
                    data.problems.add(n.key() + ": waits for " + x.key() + ", which does not exist");
            if (i.prp() != null && n.prp() == null) data.problems.add(n.key() + ": its PRP " + i.prp() + " is not in " + n.project() + " (renumbered or main file missing?)");
            if (live.contains(i.status()) && n.prp() != null && n.prp().closed()) data.problems.add(n.key() + ": still " + i.status() + " but its PRP is closed");
            if (live.contains(i.status())) for (var in : n.ins()) {
                var dep = g.nodes().get(in);
                if ("dropped".equals(dep.item().status())) data.problems.add(n.key() + ": waits for " + in + ", which was dropped");
            }
            if ("doing".equals(i.status())) {
                if (i.session() == null) data.problems.add(n.key() + ": doing with no session");
                var c = g.claim(n.key());
                if (c != null && !c.live()) data.problems.add(n.key() + ": doing, but stale claim (" + c.why() + "): set it open or done");
                for (var in : n.ins()) if (!g.nodes().get(in).done()) data.problems.add(n.key() + ": doing but waits for " + in + ", which is not done");
            }
        }
        for (var r : g.resources()) if (r.holders().size() > r.capacity())
            data.problems.add(r.name() + ": held by " + String.join(", ", r.holders()) + ", more than its capacity " + r.capacity());
        for (var cycle : cycles(g)) data.problems.add("dependency cycle: " + cycle);
        var names = new LinkedHashMap<String, String>();
        for (var r : g.resources()) {
            var low = r.name().toLowerCase();
            if (names.containsKey(low)) data.problems.add("resource names differ only in case: " + names.get(low) + " and " + r.name());
            else names.put(low, r.name());
        }
    }

    static List<String> cycles(WorkGraph g) {
        var out = new ArrayList<String>();
        var done = new HashSet<String>();
        for (var k : g.nodes().keySet()) walk(g, k, new ArrayList<>(), done, out);
        return out;
    }

    static void walk(WorkGraph g, String k, List<String> path, Set<String> done, List<String> out) {
        if (done.contains(k)) return;
        var at = path.indexOf(k);
        if (at >= 0) {
            var loop = new ArrayList<>(path.subList(at, path.size()));
            loop.add(k);
            out.add(String.join(" -> ", loop));
            return;
        }
        path.add(k);
        for (var i : g.nodes().get(k).ins()) walk(g, i, path, done, out);
        path.remove(path.size() - 1);
        done.add(k);
    }
}
