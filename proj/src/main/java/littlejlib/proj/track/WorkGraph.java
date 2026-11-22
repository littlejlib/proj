package littlejlib.proj.track;

import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The work items of several projects as one dependency graph, computed from the files alone.
 * A node key is {@code project#id}. An edge runs from the item that must come first to the one that waits.
 * Everything the report shows (what waits on the user, what is ready, the path to a goal, the layers
 * of the drawing) comes from here, so the page is a plain rendering of these answers.
 */
public final class WorkGraph {

    public record Node(String key, String project, Item item, String tag, Prp prp, Set<String> ins, Set<String> outs) {
        public boolean done() {
            return "done".equals(item.status()) || "dropped".equals(item.status());
        }

        public boolean forUser() {
            return !"todo".equals(item.type()) || "user".equals(item.owner());
        }
    }

    /** The PRP an item belongs to; {@code number} and {@code name} are null when the uuid matches no PRP of that project. */
    public record Prp(String number, String name, boolean closed) { }

    public record Edge(String from, String to, boolean cross) { }

    static final Pattern REF = Pattern.compile("^(after|blocks):\\s*([A-Za-z0-9_-]+)#(\\d+)$");
    static final Map<String, Integer> WEIGHT = Map.of("critical", 3, "infra", 2, "extra", 1);

    final Map<String, Node> nodes = new LinkedHashMap<>();
    final List<Edge> edges = new ArrayList<>();
    final Map<String, Integer> unblocks = new HashMap<>();

    /** The PRPs of one prp folder by uuid. Reading them is the slow part on a network drive, so a caller may cache it. */
    public interface PrpSource {
        Map<String, Prp> prps(Path prpDir) throws IOException;
    }

    public static Map<String, Prp> readPrps(Path prpDir) throws IOException {
        var prps = new HashMap<String, Prp>();
        for (var p : littlejlib.proj.prp.PrpCreator.list(prpDir)) {
            var uuid = littlejlib.proj.prp.PrpUuid.read(p.getMainFile());
            if (uuid != null) prps.put(uuid, new Prp(p.getIndex(), p.getName(), p.getStatus() == PrpStatus.CLOSED));
        }
        return prps;
    }

    /** When the item's current status began, from its history (the first line of the last run with that status). */
    final Map<String, String> statusSince = new HashMap<>();
    /** Claims of doing items, worked out once per load. */
    final Map<String, Claims.Liveness> claims = new HashMap<>();
    /** Every project's PRPs as loaded, for the report's PRP list. */
    final Map<String, Map<String, Prp>> prpsByProject = new LinkedHashMap<>();

    public static WorkGraph load(Map<String, Path> projects) throws IOException {
        return load(projects, WorkGraph::readPrps);
    }

    /** {@code projects}: short name to project directory (the folder holding {@code prp/}). Missing workplans are skipped. */
    public static WorkGraph load(Map<String, Path> projects, PrpSource source) throws IOException {
        var g = new WorkGraph();
        var names = new HashMap<String, String>();
        for (var e : projects.entrySet()) {
            names.put(e.getKey().toLowerCase(), e.getKey());
            var pj = littlejlib.proj.prp.ProjFile.read(e.getValue());
            if (pj != null && pj.uuid() != null) names.put(pj.uuid(), e.getKey());
        }
        var now = java.time.Instant.now();
        for (var e : projects.entrySet()) {
            var prpDir = e.getValue().resolve("prp");
            var prps = source.prps(prpDir);
            g.prpsByProject.put(e.getKey(), prps);
            var lines = Workplan.lines(prpDir);
            for (var i : Workplan.fold(lines).values()) {
                var key = e.getKey() + "#" + i.id();
                g.nodes.put(key, new Node(key, e.getKey(), i, tag(i), prps.get(i.prp()), new LinkedHashSet<>(), new LinkedHashSet<>()));
                if ("doing".equals(i.status())) g.claims.put(key, Claims.liveness(i, now));
            }
            var since = new HashMap<Long, String>();
            var last = new HashMap<Long, String>();
            for (var l : lines) {
                if (l.id() == null) continue;
                if (!java.util.Objects.equals(last.get(l.id()), l.status())) since.put(l.id(), l.updated());
                last.put(l.id(), l.status());
            }
            since.forEach((id, at) -> g.statusSince.put(e.getKey() + "#" + id, at));
        }
        for (var n : List.copyOf(g.nodes.values())) {
            if (n.item().deps() != null) for (var d : n.item().deps()) g.link(n.project() + "#" + d, n.key(), false);
            if (n.item().xdeps() != null) for (var x : n.item().xdeps()) {
                var name = x.uuid() != null && names.containsKey(x.uuid()) ? names.get(x.uuid())
                        : names.getOrDefault(String.valueOf(x.project()).toLowerCase(), x.project());
                g.link(name + "#" + x.id(), n.key(), true);
            }
            if (n.item().refs() != null) for (var r : n.item().refs()) {
                var m = REF.matcher(r.trim());
                if (!m.matches()) continue;
                var other = names.getOrDefault(m.group(2).toLowerCase(), m.group(2)) + "#" + m.group(3);
                if (m.group(1).equals("after")) g.link(other, n.key(), true);
                else g.link(n.key(), other, true);
            }
        }
        for (var n : g.nodes.values()) g.unblocks.put(n.key(), (int) g.downstream(n.key()).stream().filter(k -> !g.nodes.get(k).done()).count());
        return g;
    }

    /** Projects that items point at but this graph did not load ("uuid name" when the link has a uuid, else the name): what {@code follow} adds. */
    public static java.util.Set<String> pointedAt(Map<String, Path> projects) throws IOException {
        var have = new HashSet<String>();
        for (var e : projects.entrySet()) {
            have.add(e.getKey().toLowerCase());
            var pj = littlejlib.proj.prp.ProjFile.read(e.getValue());
            if (pj != null && pj.uuid() != null) have.add(pj.uuid());
        }
        var out = new java.util.TreeSet<String>();
        for (var p : projects.values())
            for (var i : Workplan.current(p.resolve("prp")).values()) {
                if (i.xdeps() != null) for (var x : i.xdeps())
                    if (!(x.uuid() != null && have.contains(x.uuid())) && !have.contains(String.valueOf(x.project()).toLowerCase()))
                        out.add(x.uuid() != null ? x.uuid() + " " + x.project() : x.project());
                if (i.refs() != null) for (var r : i.refs()) {
                    var m = REF.matcher(r.trim());
                    if (m.matches() && !have.contains(m.group(2).toLowerCase())) out.add(m.group(2));
                }
            }
        return out;
    }

    public String statusSince(String key) {
        return statusSince.get(key);
    }

    public Claims.Liveness claim(String key) {
        return claims.get(key);
    }

    public Map<String, Map<String, Prp>> prps() {
        return prpsByProject;
    }

    /** A live doing item: it holds what it uses. */
    public boolean holding(String key) {
        var c = claims.get(key);
        return c != null && c.live();
    }

    /** One shared resource in scope: who holds it, the ready items queued for it in order, items on it still waiting for deps. */
    public record Resource(String name, int capacity, List<String> holders, List<String> stale, List<String> queue, List<String> blocked) { }

    /** Ready items split by shared resources: who may start now together, who waits for a turn, who waits for a running holder. */
    public record Start(List<String> startNow, Map<String, String> queuedBehind, Map<String, List<String>> heldBy) { }

    public Start start() {
        var ready = readyNow();
        var cap = capacities();
        var used = new HashMap<String, List<String>>();     // resource -> live holders, then picks
        for (var k : claims.keySet()) if (holding(k)) for (var u : uses(k)) used.computeIfAbsent(u, x -> new ArrayList<>()).add(k);
        var start = new ArrayList<String>();
        var behind = new LinkedHashMap<String, String>();
        var heldBy = new LinkedHashMap<String, List<String>>();
        for (var n : ready) {
            var k = n.key();
            var holders = new ArrayList<String>();
            String turn = null;
            for (var u : uses(k)) {
                var taken = used.getOrDefault(u, List.of());
                if (taken.size() < cap.getOrDefault(u, 1)) continue;
                var running = taken.stream().filter(this::holding).toList();
                if (running.size() >= cap.getOrDefault(u, 1)) holders.addAll(running);
                else if (turn == null) turn = taken.stream().filter(t -> !holding(t)).findFirst().orElse(null);
            }
            if (!holders.isEmpty()) heldBy.put(k, holders);
            else if (turn != null) behind.put(k, turn);
            else {
                start.add(k);
                for (var u : uses(k)) used.computeIfAbsent(u, x -> new ArrayList<>()).add(k);
            }
        }
        return new Start(start, behind, heldBy);
    }

    /** Resource names an item uses, without their capacity suffix. */
    public List<String> uses(String key) {
        var u = nodes.get(key).item().uses();
        return u == null ? List.of() : u.stream().map(Claims::name).distinct().toList();
    }

    /** Capacity of each resource: the highest {@code *N} any item gives it, else 1. */
    Map<String, Integer> capacities() {
        var cap = new HashMap<String, Integer>();
        for (var n : nodes.values()) if (n.item().uses() != null)
            for (var u : n.item().uses()) cap.merge(Claims.name(u), Claims.capacity(u), Math::max);
        return cap;
    }

    public List<Resource> resources() {
        var cap = capacities();
        var start = start();
        var ready = new LinkedHashSet<String>();
        readyNow().forEach(n -> ready.add(n.key()));
        var out = new ArrayList<Resource>();
        for (var r : new java.util.TreeSet<>(cap.keySet())) {
            var holders = new ArrayList<String>();
            var stale = new ArrayList<String>();
            var queue = new ArrayList<String>();
            var blocked = new ArrayList<String>();
            for (var n : nodes.values()) {
                if (!uses(n.key()).contains(r) || n.done()) continue;
                if (claims.containsKey(n.key())) (holding(n.key()) ? holders : stale).add(n.key());
            }
            for (var k : ready) if (uses(k).contains(r) && !start.startNow().contains(k)) queue.add(k);
            for (var n : nodes.values())
                if (uses(n.key()).contains(r) && !n.done() && "open".equals(n.item().status()) && !ready.contains(n.key())) blocked.add(n.key());
            out.add(new Resource(r, cap.get(r), holders, stale, queue, blocked));
        }
        return out;
    }

    static String tag(Item i) {
        return Workplan.tagOf(i);
    }

    void link(String from, String to, boolean cross) {
        if (from.equals(to) || !nodes.containsKey(from) || !nodes.containsKey(to)) return;
        if (!nodes.get(to).ins().add(from)) return;
        nodes.get(from).outs().add(to);
        edges.add(new Edge(from, to, cross || !nodes.get(from).project().equals(nodes.get(to).project())));
    }

    public Map<String, Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public int unblocks(String key) {
        return unblocks.getOrDefault(key, 0);
    }

    /** Tag weight x 10 plus what it unblocks: the order of "ready to run". */
    public int score(String key) {
        return WEIGHT.get(nodes.get(key).tag()) * 10 + unblocks(key);
    }

    public Set<String> downstream(String key) {
        return reach(key, true);
    }

    public Set<String> upstream(String key) {
        return reach(key, false);
    }

    Set<String> reach(String key, boolean down) {
        var seen = new LinkedHashSet<String>();
        var todo = new ArrayList<String>(List.of(key));
        while (!todo.isEmpty()) {
            var n = nodes.get(todo.remove(todo.size() - 1));
            if (n == null) continue;
            for (var o : down ? n.outs() : n.ins()) if (seen.add(o)) todo.add(o);
        }
        seen.remove(key);
        return seen;
    }

    /** Open items that wait for the user, the one that unblocks most first. */
    public List<Node> waitingOnUser() {
        return nodes.values().stream().filter(n -> !n.done() && n.forUser() && "open".equals(n.item().status()))
                .sorted(Comparator.comparingInt((Node n) -> -unblocks(n.key())).thenComparingInt(n -> -score(n.key()))).toList();
    }

    /** Open todos for someone other than the user whose prerequisites are all done, most important first. */
    public List<Node> readyNow() {
        return nodes.values().stream()
                .filter(n -> !n.done() && "todo".equals(n.item().type()) && !n.forUser() && "open".equals(n.item().status()))
                .filter(n -> n.ins().stream().allMatch(k -> nodes.get(k).done()))
                .sorted(Comparator.comparingInt((Node n) -> -score(n.key()))).toList();
    }

    /** The goal and everything it waits for, prerequisites first. Empty when the goal is unknown. */
    public List<Node> pathTo(String goal) {
        if (!nodes.containsKey(goal)) return List.of();
        var need = new LinkedHashSet<>(upstream(goal));
        need.add(goal);
        var out = new LinkedHashSet<String>();
        for (var k : need) order(k, need, out, new HashSet<>());
        return out.stream().map(nodes::get).toList();
    }

    void order(String k, Set<String> within, Set<String> out, Set<String> seen) {
        if (!seen.add(k) || out.contains(k)) return;
        for (var i : nodes.get(k).ins()) if (within.contains(i)) order(i, within, out, seen);
        out.add(k);
    }

    /**
     * Columns of the drawing for the given visible keys: a node's column is the longest chain of visible
     * prerequisites before it; within a column the order is by project, PRP and id, then settled by the
     * average row of the prerequisites. Only nodes with a visible link are placed; the rest are "loose".
     */
    public Layout layout(Set<String> visible) {
        var linked = new LinkedHashSet<String>();
        for (var e : edges) if (visible.contains(e.from()) && visible.contains(e.to())) {
            linked.add(e.from());
            linked.add(e.to());
        }
        var depth = new HashMap<String, Integer>();
        for (var k : linked) depth(k, linked, depth, new HashSet<>());
        var cols = new ArrayList<List<String>>();
        for (var k : linked) {
            while (cols.size() <= depth.get(k)) cols.add(new ArrayList<>());
            cols.get(depth.get(k)).add(k);
        }
        Comparator<String> base = Comparator.<String, String>comparing(k -> nodes.get(k).project())
                .thenComparing(k -> nodes.get(k).prp() == null ? "" : nodes.get(k).prp().number())
                .thenComparingLong(k -> nodes.get(k).item().id());
        cols.forEach(c -> c.sort(base));
        var row = new HashMap<String, Double>();
        for (int pass = 0; pass < 5; pass++) {
            for (var c : cols) for (int i = 0; i < c.size(); i++) row.put(c.get(i), (double) i);
            for (int ci = 1; ci < cols.size(); ci++) {
                var rows = row;
                cols.get(ci).sort(Comparator.<String>comparingDouble(k -> {
                    var ps = nodes.get(k).ins().stream().filter(linked::contains).toList();
                    return ps.isEmpty() ? rows.get(k) : ps.stream().mapToDouble(rows::get).average().orElse(0);
                }).thenComparing(base));
            }
        }
        var loose = visible.stream().filter(k -> !linked.contains(k)).toList();
        return new Layout(cols, loose);
    }

    int depth(String k, Set<String> linked, Map<String, Integer> memo, Set<String> stack) {
        if (memo.containsKey(k)) return memo.get(k);
        if (!stack.add(k)) return 0;
        var d = 0;
        for (var i : nodes.get(k).ins()) if (linked.contains(i)) d = Math.max(d, depth(i, linked, memo, stack) + 1);
        stack.remove(k);
        memo.put(k, d);
        return d;
    }

    public record Layout(List<List<String>> columns, List<String> loose) { }
}
