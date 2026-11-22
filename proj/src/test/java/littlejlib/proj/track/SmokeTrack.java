package littlejlib.proj.track;

import littlejlib.proj.prp.Base34;
import littlejlib.proj.prp.PrpUuid;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * PRP 55: the timeline, meta.json and the work plan, on invented fixtures in a temp folder.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.track.SmokeTrack" -Dexec.classpathScope=test
 */
public class SmokeTrack {

    public static void main(String[] args) throws Exception {
        var prp = Files.createDirectories(Files.createTempDirectory("smoketrack").resolve("billing").resolve("prp"));
        Files.writeString(prp.resolve("01-prp-export_csv.md"), "- PRP Number: 01\n- PRP Name: export csv\n\nbody\n");
        Files.writeString(prp.resolve("02-prp-import_csv.closed.md"), "- PRP Number: 02\n- PRP Name: import csv\n\nbody\n");
        var u1 = PrpUuid.ensure(prp.resolve("01-prp-export_csv.md"), false).uuid();
        var u2 = PrpUuid.ensure(prp.resolve("02-prp-import_csv.closed.md"), false).uuid();
        timeline(prp, u1);
        meta(prp, u1, u2);
        workplan(prp, u1, u2);
        concurrent(prp, u1);
        importOld(prp, u1);
        budget();
        System.out.println("ok");
        System.exit(0);
    }

    static void timeline(Path prp, String u) throws Exception {
        Timeline.add(prp, "01", u, null, "started on the exporter", null, "sess-a");
        Timeline.add(prp, "01", u, "built", "csv writer\ncompiles", List.of("commit abc123"), "sess-a");
        Timeline.add(prp, "01", u, "confirmed", "user ran it on a real file", null, "sess-b");
        Timeline.add(prp, "01", u, "note", "next: quoting of commas", null, "sess-b");
        var notes = Timeline.read(prp, "01");
        check(notes.size() == 4 && notes.get(1).text().equals("csv writer compiles"), "newlines folded into one line");
        check(Timeline.state(notes).kind().equals("confirmed"), "state is the newest state kind");
        check(Timeline.recentFrom(notes, 5) == 2, "default view starts at the newest confirmed");
        check(Timeline.sessions(notes).equals(List.of("sess-a", "sess-b")), "cycles are sessions in order");
        expectFail(() -> Timeline.add(prp, "01", u, "note", "x".repeat(281), null, null), "limit is 280");
        expectFail(() -> Timeline.add(prp, "01", u, "gossip", "x", null, null), "unknown kind");
        System.out.println("  pass  timeline: append, fold newlines, state, cycles, limits");
    }

    static void meta(Path prp, String u1, String u2) throws Exception {
        MetaFile.update(prp, "01", u1, m -> m.summary("CSV export of invoices"));
        MetaFile.update(prp, "01", u1, m -> MetaFile.relate(m, u2, "depends-on", "needs the parser"));
        MetaFile.update(prp, "01", u1, m -> MetaFile.relate(m, u2, "sibling", null));
        var m = MetaFile.read(prp, "01");
        check(m.uuid().equals(u1) && m.summary().equals("CSV export of invoices"), "summary kept across updates");
        check(m.related().size() == 1 && m.related().get(0).kind().equals("sibling"), "one relation per target, replaced");
        MetaFile.update(prp, "01", u1, x -> MetaFile.relate(x, u2, null, null));
        check(MetaFile.read(prp, "01").related() == null, "relation removed");
        expectFail(() -> MetaFile.update(prp, "01", u1, x -> MetaFile.relate(x, u2, "cousin", null)), "unknown relation");
        System.out.println("  pass  meta: summary, relate, replace, remove");
    }

    static void workplan(Path prp, String u1, String u2) throws Exception {
        var a = Workplan.add(prp, new Item().type("discuss").title("which delimiter").prp(u1));
        var b = Workplan.add(prp, new Item().type("todo").title("write quoting").prp(u1).deps(List.of(a.id())));
        var c = Workplan.add(prp, new Item().type("todo").title("old parser work").prp(u2));
        check(a.id() == 1 && b.id() == 2 && c.id() == 3 && "open".equals(b.status()), "plain counter 1, 2, 3");
        Workplan.set(prp, 1, i -> i.type("todo"), "sess-b");
        Workplan.set(prp, 1, i -> i.status("done"), "sess-b");
        var now = Workplan.current(prp);
        check(now.get(1L).status().equals("done") && now.get(1L).type().equals("todo"), "last line wins");
        check(Workplan.history(prp, 1).size() == 3, "history kept");
        check(Workplan.lines(prp).size() == 5, "every change is one appended line");
        expectFail(() -> Workplan.add(prp, new Item().type("todo").title("x").deps(List.of(99L))), "no item 99");
        expectFail(() -> Workplan.set(prp, 2, i -> i.status("finished"), null), "status must be");
        Workplan.set(prp, 1, i -> i.deps(List.of(2L)), null);
        var problems = WorkplanCheck.problems(Workplan.current(prp), PrpRefs.of(prp));
        check(problems.stream().anyMatch(p -> p.startsWith("dependency cycle")), "cycle found: " + problems);
        check(problems.stream().anyMatch(p -> p.startsWith("3: still open but PRP 02 is closed")), "item of closed PRP: " + problems);
        Workplan.set(prp, 1, i -> i.deps(null), null);
        System.out.println("  pass  workplan: counter, last line wins, history, validation, check");
    }

    static void concurrent(Path prp, String u) throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var ids = java.util.Collections.synchronizedList(new ArrayList<Long>());
        var futures = new ArrayList<java.util.concurrent.Future<?>>();
        for (var t = 0; t < 40; t++) {
            var n = t;
            futures.add(pool.submit(() -> {
                try {
                    ids.add(Workplan.add(prp, new Item().type("todo").title("parallel " + n).prp(u)).id());
                    Timeline.add(prp, "01", u, "note", "parallel " + n, null, "sess-" + n);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        pool.shutdown();
        for (var f : futures) f.get();
        check(pool.awaitTermination(60, TimeUnit.SECONDS), "finished");
        check(new HashSet<>(ids).size() == 40, "40 distinct ids from 8 writers: " + ids.size());
        check(Timeline.read(prp, "01").size() == 44, "no note lost");
        check(!Files.exists(prp.resolve("workplan.jsonl.lock")), "lock released");
        System.out.println("  pass  concurrent: 8 writers, distinct ids, nothing lost");
    }

    static void importOld(Path prp, String u) throws Exception {
        var other = Files.createDirectories(prp.getParent().getParent().resolve("ledger").resolve("prp"));
        var old = other.resolve("old.jsonl");
        Files.writeString(old, "{\"id\":\"0001\",\"type\":\"todo\",\"status\":\"open\",\"title\":\"a\",\"deps\":[],\"refs\":[\"0002\"],\"owner\":\"user\"}\n"
                + "{\"id\":\"0002\",\"type\":\"confirm\",\"status\":\"hold\",\"title\":\"" + "b".repeat(120) + "\",\"deps\":[\"0001\"],\"owner\":[\"user\",\"agent\"]}\n");
        var dry = WorkplanImport.run(old, other, u, true);
        check(dry.size() == 2 && !Files.exists(Workplan.file(other)), "dry run writes nothing");
        WorkplanImport.run(old, other, u, false);
        var items = Workplan.current(other);
        check(items.get(2L).deps().equals(List.of(1L)) && items.get(1L).refs().equals(List.of("2")), "ids unpadded");
        check(items.get(2L).owner().equals("user; agent") && u.equals(items.get(1L).prp()), "owner list joined, prp filled");
        Workplan.set(other, 2, i -> i.status("done"), null);
        expectFail(() -> Workplan.set(other, 2, i -> i.title("c".repeat(101)), null), "limit is 100");
        expectFail(() -> WorkplanImport.run(old, other, u, false), "already has items");
        System.out.println("  pass  import: 0001 -> 1, deps and refs, refuses a non-empty plan");
    }

    static void budget() {
        var bytes = new ByteArrayOutputStream();
        var b = new Budget(new PrintStream(bytes), 10);
        for (var i = 0; i < 10; i++) b.line("line number " + i);
        b.end();
        var out = bytes.toString();
        check(out.contains("line number 0") && !out.contains("line number 9") && out.contains("more lines left out"), out);
        check(Base34.valid(Base34.mint()), "base34");
        System.out.println("  pass  budget: stops and counts");
    }

    interface Body {
        void run() throws Exception;
    }

    static void expectFail(Body body, String expected) {
        try {
            body.run();
        } catch (Exception e) {
            check(e.getMessage() != null && e.getMessage().contains(expected), "expected '" + expected + "', got: " + e.getMessage());
            return;
        }
        throw new AssertionError("should have failed: " + expected);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
