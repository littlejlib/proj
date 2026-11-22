package littlejlib.proj.renumber;

import littlejlib.proj.prp.ProjFile;
import littlejlib.proj.prp.ProjId;
import littlejlib.proj.prp.ProjJson;
import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpUuid;
import littlejlib.proj.track.GlobalFind;
import littlejlib.proj.track.GlobalLine;
import littlejlib.proj.track.Jsonl;
import littlejlib.proj.track.MetaFile;
import littlejlib.proj.track.PrpIndex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * PRP 56: proj prp renumber on an invented legacy folder, checked file by file.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.renumber.SmokeRenumber" -Dexec.classpathScope=test
 */
public class SmokeRenumber {

    public static void main(String[] args) throws Exception {
        var tmp = Files.createTempDirectory("smokerenumber");
        System.setProperty("user.home", tmp.resolve("home").toString());
        dryRunChangesNothing(fixture(tmp.resolve("a")));
        renumber(fixture(tmp.resolve("b")));
        refusals(tmp.resolve("c"));
        SmokeRenumberRun.twoFolders(fixture(tmp.resolve("d")));
        System.out.println("ok");
        System.exit(0);
    }

    static Path fixture(Path root) throws Exception {
        var project = Files.createDirectories(root.resolve("billing"));
        Files.createDirectories(root.resolve("acme"));
        var prp = Files.createDirectories(project.resolve("prp"));
        write(prp, "01-prp-setup_db.md", "- PRP Number: 01\n- PRP Name: setup db\n\nbody of the first\n");
        write(prp, "01-prp.status.md", "- PRP Number: 01\nsee 01-prp.01.findings.md\n");
        write(prp, "01-prp.status.jsonl", "{\"kind\":\"note\",\"text\":\"wrote 01-prp.01.alpha.md\"}\n");
        write(prp, "01-prp.meta.json", "{\"summary\":\"the database\"}\n");
        write(prp, "01-prp.01.findings.md", "findings\n");
        write(prp, "01-prp.01.alpha.md", "alpha\n");
        write(prp, "01-prp.02.md", "a note without words\n");
        write(prp, "01-prp.99.01.logs.md", "## session one on PRP 01\n");
        write(prp, "01-prp.99.01.logs.jsonl", "{\"text\":\"working on @prp/01-prp-setup_db.md\"}\n");
        write(prp, "01-prp.99.02.logs.md", "## session two\n");
        write(prp, "02-prp-export.closed.md", "- PRP Number: 02\n- PRP Name: export\n");
        write(prp, "10-prp-api.md", String.join("\n",
                "- PRP Number: 10", "- PRP Name: api", "",
                "See @prp/01-prp-setup_db.md and prp/01-prp.01.findings.md.",
                "Depends on PRP 02 and PRP-34, and billing PRP 34, during PRP 10.",
                "The acme PRP 10 is elsewhere. PRP 77 does not exist.",
                "Also `02-prp-export.md` before it was closed.",
                "Other folder: C:/work/acme/prp/01-prp.status.md",
                "Token 34-prp-ui here.",
                "Data in prp/01-prp.03.free-data/x.csv, gone: 01-prp.02.summary.md, 01-prp.07.gone.md, 01-prp-oldname.md, 77-prp.01.x.md",
                "Ledger: ledger/prp/02-prp.01.notes.md and ../ledger/prp/01-prp-intake.md",
                "lower case prp 2 stays", "● Bash(ls prp 2>/dev/null) PRP 02 stays", ""));
        write(prp, "10-prp-api.txt", "a text file that looks like a main file\n");
        Files.createDirectories(prp.resolve("01-prp.03.free-data"));
        write(prp.resolve("01-prp.03.free-data"), "x.csv", "a,b\n");
        write(prp, "01-prp.99.03.logs.md", String.join("\n", "$ ls prp 2", "● Bash(cat prp/01-prp-setup_db.md)", "PRP 77 and acme PRP 10 in a log", ""));
        write(prp, "10-prp.status.md", "status of 10\n");
        write(prp, "34-prp-ui.md", "- PRP Number: 34\n- Usage Guide: sub-files\n\t- like `01-prp.01.md`, `77-prp.02.summary.md`\n- PRP Name: ui\n");
        write(prp, "00-prp.index.md", "- 01 setup\n");
        write(prp, "README.md", "not a PRP\n");
        write(project, "CLAUDE.md", "Read prp/34-prp-ui.md first.\n");
        ProjFile.save(project, new ProjJson().uuid(ProjId.mint()).name("billing").alias3("bil").alias4("bill").longName("Billing fixture"));
        for (var f : List.of("01-prp-setup_db.md", "02-prp-export.closed.md", "10-prp-api.md", "34-prp-ui.md"))
            PrpUuid.ensure(prp.resolve(f), false);
        return prp;
    }

    static void dryRunChangesNothing(Path prp) throws Exception {
        var before = snapshot(prp.getParent().getParent());
        var r = Renumber.apply(prp, true);
        check(r.state().equals("dry-run") && r.plan().files().size() == 17, "dry run plans 17 files: " + r.state() + " " + r.why());
        check(!r.reviews().isEmpty() && !r.rewritten().isEmpty(), "dry run reports reviews and rewrites");
        check(before.equals(snapshot(prp.getParent().getParent())), "dry run created or changed a file");
        System.out.println("  pass  dry run: plan and review list, zero files created or changed");
    }

    static void renumber(Path prp) throws Exception {
        var uuid1 = PrpUuid.read(prp.resolve("01-prp-setup_db.md"));
        var r = Renumber.apply(prp, false);
        check(r.state().equals("done"), "done: " + r.state() + " " + r.why());
        var expected = List.of("000100-setup_db.md", "000101.status.md", "000101.status.jsonl", "000101.meta.json",
                "000102-alpha.md", "000103-findings.md", "000104-note.md", "000105-free-data/x.csv", "0001ZZ-01.logs.md", "0001ZZ-01.logs.jsonl",
                "0001ZZ-02.logs.md", "0001ZZ-03.logs.md", "000200-export.closed.md", "000300-api.md", "000301.status.md", "000302-api.txt", "000400-ui.md",
                "00-prp.index.md", "README.md", "index.jsonl", Renumber.MAP, Renumber.REVIEW);
        for (var e : expected) check(Files.exists(prp.resolve(e)), "missing " + e);
        try (var files = Files.list(prp)) {
            var left = files.map(p -> p.getFileName().toString()).filter(n -> n.contains("-prp") && !n.startsWith("00-prp.index")).toList();
            check(left.isEmpty(), "legacy files left: " + left);
        }
        var map = Jsonl.read(prp.resolve(Renumber.MAP), RenumberFile.class);
        check(map.size() == 17, "map lines " + map.size());
        var first = map.stream().filter(f -> f.oldFile().equals("01-prp-setup_db.md")).findFirst().orElseThrow();
        check(first.newFile().equals("000100-setup_db.md") && first.oldNumber().equals("01") && first.newNumber().equals("0001")
                && first.kind().equals("main") && uuid1.equals(first.uuid()), "map line " + Jsonl.GSON.toJson(first));
        var mapLine = Files.readAllLines(prp.resolve(Renumber.MAP)).get(0);
        check(mapLine.startsWith("{\"oldFile\":") && mapLine.contains("\"uuid\":"), "map fields " + mapLine);
        check(map.stream().anyMatch(f -> f.oldFile().equals("01-prp.99.01.logs.jsonl") && f.kind().equals("log") && f.newFile().equals("0001ZZ-01.logs.jsonl")), "log in map");

        var api = Files.readString(prp.resolve("000300-api.md"));
        for (var want : List.of("See @prp/000100-setup_db.md and prp/000103-findings.md.", "Depends on PRP 0002 and PRP-0004, and billing PRP 0004, during PRP 0003.",
                "The acme PRP 10 is elsewhere. PRP 77 does not exist.", "Also `000200-export.closed.md` before",
                "Other folder: C:/work/acme/prp/01-prp.status.md", "Token 000400-ui here.",
                "Data in prp/000105-free-data/x.csv, gone: 000104-summary.md, 000107-gone.md, 000100-oldname.md, 77-prp.01.x.md",
                "lower case prp 2 stays", "● Bash(ls prp 2>/dev/null) PRP 02 stays"))
            check(api.contains(want), "api text lacks: " + want + "\n" + api);
        check(Files.readString(prp.resolve("000101.status.md")).contains("see 000103-findings.md"), "status md rewritten");
        check(Files.readString(prp.resolve("000101.status.jsonl")).contains("wrote 000102-alpha.md"), "timeline rewritten");
        check(Files.readString(prp.resolve("0001ZZ-01.logs.jsonl")).contains("@prp/000100-setup_db.md"), "jsonl log rewritten");
        check(Files.readString(prp.resolve("0001ZZ-01.logs.md")).contains("session one on PRP 01"), "phrase in a log left as it is");
        check(Files.readString(prp.resolve("000400-ui.md")).contains("\t- like `01-prp.01.md`, `77-prp.02.summary.md`\n- PRP Name: ui"), "usage guide block left as it is");
        check(Files.readString(prp.resolve("0001ZZ-03.logs.md")).equals("$ ls prp 2\n● Bash(cat prp/000100-setup_db.md)\nPRP 77 and acme PRP 10 in a log\n"),
                "log: file names only " + Files.readString(prp.resolve("0001ZZ-03.logs.md")));
        check(r.quiet() == 0, "nothing quiet: phrases in logs are not even looked at, " + r.quiet());
        check(Files.readString(prp.getParent().resolve("CLAUDE.md")).equals("Read prp/000400-ui.md first.\n"), "CLAUDE.md rewritten");

        var reviews = Jsonl.read(prp.resolve(Renumber.REVIEW), RenumberReview.class);
        var whys = reviews.stream().map(v -> v.file() + ":" + v.line() + " " + v.why()).collect(Collectors.joining("\n"));
        check(reviews.stream().anyMatch(v -> v.file().equals("prp/000300-api.md") && v.why().contains("acme")), "acme PRP 10 reviewed:\n" + whys);
        check(reviews.stream().anyMatch(v -> v.why().contains("no PRP 77")), "PRP 77 reviewed:\n" + whys);
        check(reviews.stream().anyMatch(v -> v.why().contains("another folder")), "other folder reviewed:\n" + whys);
        check(reviews.stream().anyMatch(v -> v.why().contains("PRP 77 that does not exist: 77-prp")), "missing 77 reviewed:\n" + whys);
        check(reviews.stream().noneMatch(v -> v.file().contains("ZZ-") || v.why().contains("PRP 2") || v.text().contains("prp 2")), "no log or prp 2 reviews:\n" + whys);
        check(reviews.stream().filter(v -> v.why().contains("no PRP 02") || v.text().contains("PRP 02 stays")).findAny().isEmpty(), "command line skipped:\n" + whys);

        var meta = MetaFile.read(prp, "0001");
        check("01".equals(meta.was()) && "01-prp-setup_db.md".equals(meta.wasFile()) && "the database".equals(meta.summary())
                && uuid1.equals(meta.uuid()), "meta was/wasFile " + Jsonl.GSON.toJson(meta));
        check("10".equals(MetaFile.read(prp, "0003").was()) && "02-prp-export.closed.md".equals(MetaFile.read(prp, "0002").wasFile()), "meta of 0003, 0002");
        var index = PrpIndex.read(prp);
        check(index.size() == 4 && index.get(2).number().equals("0003") && "10".equals(index.get(2).was()), "index rebuilt with was");

        var global = index.stream().map(l -> new GlobalLine().project(prp.getParent().toString()).key("billing").number(l.number())
                .uuid(l.uuid()).name(l.name()).file(prp.resolve(l.file()).toString()).was(l.was())).toList();
        check(GlobalFind.find(global, "was:10").size() == 1 && GlobalFind.find(global, "was:10").get(0).number().equals("0003"), "find was:10");
        check(GlobalFind.find(global, "billing-3").get(0).number().equals("0003") && GlobalFind.find(global, "3").size() == 1, "find ticket any width");

        var before = snapshot(prp.getParent());
        var again = Renumber.apply(prp, false);
        check(again.state().equals("new") && before.equals(snapshot(prp.getParent())), "second run is a no-op: " + again.state());
        var next = PrpCreator.create(prp, "next one", null, null);
        check(next.index().equals("0005") && next.file().getFileName().toString().equals("000500-next_one.md"), "next PRP " + next.file());
        System.out.println("  pass  renumber: names, map, references, review list, was/wasFile, index, find was:, idempotent, next number");
    }

    static void refusals(Path root) throws Exception {
        var prp = Files.createDirectories(root.resolve("acme").resolve("prp"));
        write(prp, "01-prp-x.md", "x");
        write(prp, "01-prp-y.md", "y");
        var open = Renumber.apply(prp, false);
        check(open.state().equals("refused") && open.why().contains("several open main files: 01-prp-x.md, 01-prp-y.md")
                && Files.exists(prp.resolve("01-prp-x.md")), "two open mains refused: " + open.why());
        Files.delete(prp.resolve("01-prp-y.md"));
        write(prp, "1-prp-z.md", "x");
        check(Renumber.apply(prp, false).state().equals("refused") && Files.exists(prp.resolve("01-prp-x.md")), "unparsable refused");
        Files.delete(prp.resolve("1-prp-z.md"));
        write(prp, "000200-y.md", "y");
        var r = Renumber.apply(prp, false);
        check(r.state().equals("refused") && r.why().contains("both schemes"), "mixed refused: " + r.why());
        System.out.println("  pass  refuses unparsable and mixed folders, touching nothing");
    }

    static Map<String, String> snapshot(Path root) throws Exception {
        try (var walk = Files.walk(root)) {
            var out = new TreeMap<String, String>();
            for (var p : walk.filter(Files::isRegularFile).toList())
                out.put(root.relativize(p).toString(), Files.readString(p) + "|" + Files.getLastModifiedTime(p).toMillis());
            return out;
        }
    }

    static void write(Path dir, String name, String text) throws Exception {
        Files.writeString(dir.resolve(name), text);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
