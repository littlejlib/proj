package littlejlib.proj.track;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * The graph of work items across projects: links, tags, what waits on the user, what is ready, the path, the layers.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.track.SmokeWorkGraph" -Dexec.classpathScope=test
 */
public class SmokeWorkGraph {

    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("smokegraph");
        var billing = Files.createDirectories(root.resolve("billing").resolve("prp"));
        var ledger = Files.createDirectories(root.resolve("ledger").resolve("prp"));

        var a = Workplan.add(billing, new Item().type("todo").title("schema").detail("[critical] base"));
        var b = Workplan.add(billing, new Item().type("confirm").title("approve schema").deps(List.of(a.id())));
        var c = Workplan.add(billing, new Item().type("todo").title("exporter").deps(List.of(b.id())).owner("fclaude"));
        var d = Workplan.add(billing, new Item().type("todo").title("ready one").detail("[critical] the field wins").tag("extra"));
        var e = Workplan.add(ledger, new Item().type("todo").title("use the export").refs(List.of("after:billing#" + c.id())));
        var f = Workplan.add(ledger, new Item().type("todo").title("lone item"));
        Workplan.set(billing, a.id(), i -> i.status("done"), null);

        var projects = new LinkedHashMap<String, java.nio.file.Path>();
        projects.put("billing", billing.getParent());
        projects.put("ledger", ledger.getParent());
        var g = WorkGraph.load(projects);

        check(g.nodes().size() == 6, "six nodes");
        check(g.nodes().get("billing#1").tag().equals("critical") && g.nodes().get("billing#4").tag().equals("extra")
                && g.nodes().get("ledger#2").tag().equals("infra"), "tags from the detail, infra by default");
        check(g.edges().size() == 3 && g.edges().stream().filter(WorkGraph.Edge::cross).count() == 1, "three edges, one across projects");
        check(g.unblocks("billing#2") == 2, "approve schema unblocks exporter and the ledger item: " + g.unblocks("billing#2"));
        var waiting = g.waitingOnUser();
        check(waiting.size() == 1 && waiting.get(0).key().equals("billing#2"), "one waits on the user: " + waiting);
        var ready = g.readyNow().stream().map(WorkGraph.Node::key).toList();
        check(ready.equals(List.of("ledger#2", "billing#4")), "ready now, infra before extra (exporter waits on the confirm): " + ready);
        var path = g.pathTo("ledger#1").stream().map(WorkGraph.Node::key).toList();
        check(path.equals(List.of("billing#1", "billing#2", "billing#3", "ledger#1")), "path, prerequisites first: " + path);
        check(g.pathTo("nope#9").isEmpty(), "unknown goal gives an empty path");

        var lay = g.layout(Set.copyOf(g.nodes().keySet()));
        check(lay.columns().size() == 4 && lay.columns().get(3).equals(List.of("ledger#1")), "four columns: " + lay.columns());
        check(lay.loose().containsAll(List.of("billing#4", "ledger#2")) && lay.loose().size() == 2, "loose items: " + lay.loose());
        System.out.println("  pass  work graph: links, tags, waiting, ready, path, layers");

        // shared resources: the 25/28/31/36 case in small
        var core = Files.createDirectories(root.resolve("core").resolve("prp"));
        var c25 = Workplan.add(core, new Item().type("todo").title("footnotes").uses(List.of("git:core")));
        var c28 = Workplan.add(core, new Item().type("todo").title("numbering").tag("critical").uses(List.of("git:core")));
        var c31 = Workplan.add(core, new Item().type("todo").title("sections").uses(List.of("git:core")));
        var c36 = Workplan.add(core, new Item().type("todo").title("view nav").uses(List.of("git:view")));
        var c40 = Workplan.add(core, new Item().type("todo").title("free a").uses(List.of("account:free*2")));
        var c41 = Workplan.add(core, new Item().type("todo").title("free b").uses(List.of("account:free")));
        var c42 = Workplan.add(core, new Item().type("todo").title("free c").uses(List.of("account:free")));
        var only = new LinkedHashMap<String, java.nio.file.Path>();
        only.put("core", core.getParent());
        var s = WorkGraph.load(only).start();
        check(s.startNow().equals(List.of("core#2", "core#4", "core#5", "core#6")), "start now, one per resource, two on free*2: " + s.startNow());
        check("core#2".equals(s.queuedBehind().get("core#1")) && "core#2".equals(s.queuedBehind().get("core#3"))
                && "core#5".equals(s.queuedBehind().get("core#7")), "25 and 31 queued behind 28, free c behind free a: " + s.queuedBehind());

        Workplan.set(core, c28.id(), i -> i.status("doing"), null);   // no session: liveness by updated, so live
        var g2 = WorkGraph.load(only);
        var s2 = g2.start();
        check(s2.heldBy().get("core#1").equals(List.of("core#2")) && s2.heldBy().get("core#3").equals(List.of("core#2")),
                "25 and 31 held by running 28: " + s2.heldBy());
        check(s2.startNow().contains("core#4") && !s2.startNow().contains("core#1"), "view still starts: " + s2.startNow());
        check(g2.statusSince("core#2") != null, "status since recorded");
        var res = g2.resources().stream().filter(r -> r.name().equals("git:core")).findFirst().orElseThrow();
        check(res.holders().equals(List.of("core#2")) && res.queue().containsAll(List.of("core#1", "core#3")), "resource panel: " + res);
        check(g2.resources().stream().anyMatch(r -> r.name().equals("account:free") && r.capacity() == 2), "capacity is the highest given");
        System.out.println("  pass  shared resources: start now, queued, held by, capacity, status since");

        // native links across projects, by uuid even after a rename, and a cycle across projects
        Files.writeString(core.resolve("proj.json"), "{\"uuid\":\"coreuuid\",\"name\":\"core\"}");
        var docs = Files.createDirectories(root.resolve("docs").resolve("prp"));
        var d1 = Workplan.add(docs, new Item().type("todo").title("document numbering")
                .xdeps(List.of(new Link().project("oldname").uuid("coreuuid").id(c28.id()))));
        var both = new LinkedHashMap<String, java.nio.file.Path>();
        both.put("docs", docs.getParent());
        both.put("core", core.getParent());
        var g3 = WorkGraph.load(both);
        check(g3.nodes().get("docs#1").ins().contains("core#2"), "xdeps found by uuid although the name changed: " + g3.nodes().get("docs#1").ins());
        check(g3.edges().stream().anyMatch(x -> x.cross() && x.from().equals("core#2")), "a cross edge");
        var onlyDocs = new LinkedHashMap<String, java.nio.file.Path>();
        onlyDocs.put("docs", docs.getParent());
        check(WorkGraph.pointedAt(onlyDocs).contains("coreuuid oldname"), "follow names the uuid and the name: " + WorkGraph.pointedAt(onlyDocs));
        Workplan.set(core, c28.id(), i -> i.xdeps(List.of(new Link().project("docs").id(d1.id()))), null);
        var report = littlejlib.proj.report.ReportBuilder.build(littlejlib.proj.report.ReportQuery.of(java.util.Map.of(
                "with", docs.getParent() + "," + core.getParent())), null, WorkGraph::readPrps);
        check(report.problems.stream().anyMatch(p -> p.startsWith("dependency cycle") && p.contains("docs#1") && p.contains("core#2")), "cycle across projects: " + report.problems);
        System.out.println("  pass  links across projects: uuid wins, follow, cycle");
        System.out.println("ok");
        System.exit(0);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
