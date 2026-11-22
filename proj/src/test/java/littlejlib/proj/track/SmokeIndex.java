package littlejlib.proj.track;

import littlejlib.proj.prp.PrpUuid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PRP 55 piece 2: index.jsonl and the 00-prp.index.md migration, on invented fixtures.
 * With file arguments it also prints what it reads from those real index files (nothing is written).
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.track.SmokeIndex" -Dexec.classpathScope=test [-Dexec.args="file ..."]
 */
public class SmokeIndex {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            for (var a : args) {
                var found = IndexMigrate.summaries(Files.readAllLines(Path.of(a)));
                System.out.println("== " + a + "  " + found.size() + " summaries");
                found.entrySet().stream().limit(6).forEach(e -> System.out.println("  " + e.getKey() + "  " + e.getValue()));
            }
            System.exit(0);
        }
        shapes();
        indexAndMigrate();
        System.out.println("ok");
        System.exit(0);
    }

    static void shapes() {
        var lines = List.of(
                "# Index", "Some project prose that is not a PRP line.",
                "- 01 exporter umbrella: why it exists -- CLOSED 2026-10-20",
                "- `02-prp-parser.closed.md` - (was 100) parses **csv** files; done",
                "- 03-prp-report.md",
                "\t- Scope: Monthly report of invoices",
                "\t- Status: open",
                "- A4-prp-files_only.md",
                "\t- PRP-Sub-Files: (i) 01 notes",
                "\t- Notes: the first real note",
                "\t- 05 is not a top-level line",
                "- 01 a second mention, ignored");
        var s = IndexMigrate.summaries(lines);
        check(s.get("01").equals("exporter umbrella: why it exists -- CLOSED 2026-10-20"), "shape 1: " + s.get("01"));
        check(s.get("02").equals("parses csv files; done"), "shape 3: " + s.get("02"));
        check(s.get("03").equals("Monthly report of invoices"), "shape 2 scope: " + s.get("03"));
        check(s.get("A4").equals("the first real note"), "files label skipped: " + s.get("A4"));
        check(!s.containsKey("05") && s.size() == 4, "sub-bullets are not PRP lines: " + s.keySet());
        check(IndexMigrate.cut("word ".repeat(80)).length() <= MetaFile.MAX_SUMMARY, "cut to the summary limit");
        System.out.println("  pass  shapes: three line shapes, first mention wins, prose ignored");
    }

    static void indexAndMigrate() throws Exception {
        var prp = Files.createDirectories(Files.createTempDirectory("smokeindex").resolve("billing").resolve("prp"));
        for (var n : List.of("01-prp-export.md", "02-prp-parser.closed.md", "03-prp-report.md"))
            Files.writeString(prp.resolve(n), "- PRP Number: " + n.substring(0, 2) + "\n- PRP Name: x\n\nbody\n");
        Files.writeString(prp.getParent().resolve("CLAUDE.md"), "# billing\nexisting rule");
        Files.writeString(prp.resolve(IndexMigrate.OLD), "# Billing PRPs\nRule one: invoices are immutable.\nRule two: amounts in cents.\n\n- 01 exports invoices\n- 02 parses csv\n- 07 a PRP that is gone\n");
        var u1 = PrpUuid.ensure(prp.resolve("01-prp-export.md"), false).uuid();
        var u2 = PrpUuid.ensure(prp.resolve("02-prp-parser.closed.md"), false).uuid();
        MetaFile.update(prp, "02", u2, m -> m.summary("kept, written by hand"));
        MetaFile.update(prp, "01", u1, m -> MetaFile.relate(m, u2, "supersedes", null));
        Timeline.add(prp, "01", u1, "built", "first cut", null, "s1");
        Workplan.add(prp, new Item().type("todo").title("finish").prp(u1));

        var dry = IndexMigrate.apply(prp, true);
        check(dry.written() == 1 && dry.kept() == 1 && Files.exists(prp.resolve(IndexMigrate.OLD)), "dry run changes nothing");
        var r = IndexMigrate.apply(prp, false);
        check(r.written() == 1 && r.notInOld().equals(List.of("03")) && r.notPrps().equals(List.of("07")), "report: " + r);
        check(!Files.exists(prp.resolve(IndexMigrate.OLD)) && Files.exists(prp.resolve(IndexMigrate.RENAMED)), "old file renamed");
        check(MetaFile.read(prp, "01").summary().equals("exports invoices"), "summary imported");
        check(Files.readString(prp.resolve(Preamble.FILE)).startsWith("# Billing PRPs\nRule one") && r.preamble().startsWith("moved 4"), "preamble moved: " + r.preamble());
        var claude = Files.readString(prp.getParent().resolve("CLAUDE.md"));
        check(claude.startsWith("# billing\nexisting rule\n\n- Project preamble") && !claude.contains("@prp") && claude.contains("may be outdated"), "CLAUDE.md line: " + claude);
        check(MetaFile.read(prp, "02").summary().equals("kept, written by hand"), "existing summary kept");

        var lines = PrpIndex.build(prp);
        PrpIndex.write(prp, lines);
        var l1 = lines.get(0);
        var l2 = lines.get(1);
        check(lines.size() == 3 && l1.uuid().equals(u1) && "built".equals(l1.progress()) && l1.openItems() == 1 && l1.notes() == 1, "line 01");
        check("closed".equals(l2.state()) && l2.related().get(0).kind().equals("superseded-by") && l2.related().get(0).derived(), "reverse relation derived");
        check(IndexCheck.withoutUuid(lines).equals(List.of("03")), "03 has no uuid, and indexing did not give it one");
        check(PrpUuid.read(prp.resolve("03-prp-report.md")) == null, "index never writes into a PRP");
        check(IndexCheck.stale(PrpIndex.read(prp), PrpIndex.build(prp)).isEmpty(), "fresh index is not stale");
        Thread.sleep(1100);
        Timeline.add(prp, "01", u1, "tested", "works", null, "s1");
        Files.writeString(prp.resolve("04-prp-new.md"), "- PRP Number: 04\n");
        var stale = IndexCheck.stale(PrpIndex.read(prp), PrpIndex.build(prp));
        check(stale.size() == 2 && stale.get(0).startsWith("01: stale  progress, notes, updated") && stale.get(1).startsWith("04: missing"), "stale: " + stale);
        System.out.println("  pass  index: build, derived reverse, read-only, migrate, stale report");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
