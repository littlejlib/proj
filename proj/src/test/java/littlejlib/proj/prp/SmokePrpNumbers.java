package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * PRP 56: four-symbol base34 numbers, the new file names, short forms; legacy (PRP 54) folders still work.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.prp.SmokePrpNumbers" -Dexec.classpathScope=test
 */
public class SmokePrpNumbers {

    public static void main(String[] args) throws Exception {
        sequence();
        parsing();
        shortForms();
        fileNames();
        newFolder();
        legacyFolder();
        legacyPastNinetyNine();
        refusesWhenUsedUp();
        System.out.println("ok");
        System.exit(0);
    }

    static void sequence() {
        var seen = new HashSet<String>();
        for (var i = 0; i < 3000; i++) {
            var code = PrpNumber.of(i);
            check(PrpNumber.valid(code) && PrpNumber.ordinal(code) == i, "round trip " + i + " " + code);
            check(!code.contains("I") && !code.contains("O") && seen.add(code), "no I or O, distinct: " + code);
            if (i > 0) check(PrpNumber.of(i - 1).compareTo(code) < 0, "string order is PRP order at " + code);
        }
        check(PrpNumber.capacity() == 1336336, "capacity " + PrpNumber.capacity());
        check("000A".equals(PrpNumber.next("0009")) && "0010".equals(PrpNumber.next("000Z")) && "000J".equals(PrpNumber.next("000H")), "0009 000A, 000Z 0010, H J");
        check("001M".equals(PrpNumber.of(55)) && "002Y".equals(PrpNumber.of(100)) && "000Z".equals(PrpNumber.of(33)), "decision 3 examples");
        check("ZZZZ".equals(PrpNumber.of(PrpNumber.capacity() - 1)) && PrpNumber.next("ZZZZ") == null, "ZZZZ is last");
        var legacy = new HashSet<String>();
        for (var i = 0; i < PrpNumber.legacyCapacity(); i++) {
            var code = PrpNumber.ofLegacy(i);
            check(PrpNumber.validLegacy(code) && PrpNumber.ordinal(code) == i && legacy.add(code), "legacy round trip " + code);
        }
        check("A0".equals(PrpNumber.next("99")) && "B0".equals(PrpNumber.next("AZ")) && PrpNumber.next("ZZ") == null, "legacy 99 A0, AZ B0, ZZ last");
        System.out.println("  pass  sequence: pure base34 0001..ZZZZ, legacy 00..ZZ");
    }

    static void parsing() {
        check("0001".equals(PrpNumber.parse("1")) && "0001".equals(PrpNumber.parse("01")) && "0001".equals(PrpNumber.parse("0001")), "any width");
        check("001M".equals(PrpNumber.parse("1m")) && "0010".equals(PrpNumber.parse("io")) && "00A0".equals(PrpNumber.parse("ao")), "case, o, i");
        for (var bad : List.of("12345", "", "A-", "x y", "-1")) {
            try {
                PrpNumber.parse(bad);
                check(false, "should refuse '" + bad + "'");
            } catch (NumberFormatException expected) { }
        }
        check("A0".equals(PrpNumber.parseLegacy("ao")) && "04".equals(PrpNumber.parseLegacy("4")), "legacy parse");
        check(PrpNumber.same("55", "55") && PrpNumber.same("55", "0055") && PrpNumber.same("1m", "001M") && !PrpNumber.same("1M", "1M0Z"), "same()");
        check(PrpNumber.same("a0", "A0") && PrpNumber.same("a0", "00A0") && !PrpNumber.same("zzzzz", "ZZZZ"), "same() per scheme");
        System.out.println("  pass  parsing");
    }

    static void shortForms() {
        check(PrpNumber.width("0001") == 1 && PrpNumber.width("000Z") == 1 && PrpNumber.width("0010") == 2 && PrpNumber.width(null) == 1, "width");
        check("1".equals(PrpNumber.shortForm("0001", 1)) && "01".equals(PrpNumber.shortForm("0001", 2)), "short 1, 01");
        check("1M".equals(PrpNumber.shortForm("001M", 1)) && "0001".equals(PrpNumber.shortForm("0001", 4)) && "55".equals(PrpNumber.shortForm("55", 1)), "never cut, legacy as is");
        System.out.println("  pass  short forms");
    }

    static void fileNames() {
        check(PrpFiles.isNew("001M00-x.md") && PrpFiles.isNew("001M01.meta.json") && PrpFiles.isNew("001MZZ-01.logs.md"), "new names");
        check(!PrpFiles.isNew("README.md") && !PrpFiles.isNew("001m00-x.md") && !PrpFiles.isNew("index.jsonl"), "README, lower case, index are not PRP files");
        check("001M".equals(PrpFiles.numberOf("001M02-findings.md")) && "55".equals(PrpFiles.numberOf("55-prp.01.findings.md")), "numberOf");
        check(PrpFiles.numberOf("00-prp.index.md") == null, "old index file is not PRP 00");
        check("001M00-x.closed.md".equals(PrpFiles.main("001M", "x", true)) && "55-prp-x.md".equals(PrpFiles.main("55", "x", false)), "main names");
        check("001MZZ-0A.logs.jsonl".equals(PrpFiles.log("001M", 10, "jsonl")) && "55-prp.99.10.logs.md".equals(PrpFiles.log("55", 10, "md")), "log names");
        System.out.println("  pass  file names");
    }

    static void newFolder() throws Exception {
        var prp = project().resolve("prp");
        Files.writeString(prp.resolve("README.md"), "not a PRP");
        var a = PrpCreator.create(prp, "first new", null, null);
        var b = PrpCreator.create(prp, "second new", null, null);
        check("0001".equals(a.index()) && "0002".equals(b.index()), "new folder: " + a.index() + " " + b.index());
        check(a.file().getFileName().toString().equals("000100-first_new.md"), "main name " + a.file().getFileName());
        check(!Files.exists(prp.resolve("000101.status.md")), "no status markdown file (the timeline is status.jsonl)");
        var head = PrpHeader.of(a.file());
        check(head.state() == PrpState.NEW && head.createdUtc != null && head.lastWorkedOnUtc != null, "a new PRP starts as new, created now: " + head.status + " " + head.createdUtc);
        Files.writeString(prp.resolve("000901.status.md"), "orphan");
        check("000A".equals(PrpCreator.create(prp, "after orphan", null, null).index()), "an orphan status file holds its number, then 000A");
        check(AgentLauncher.findPrp(prp.getParent(), "1").getFileName().toString().equals("000100-first_new.md"), "findPrp 1");
        check("0001ZZ-01.logs.md".equals(PrpLogs.nextName(prp, "0001")), "log name");
        Files.writeString(prp.resolve("0001ZZ-01.logs.md"), "x");
        check("0001ZZ-02.logs.md".equals(PrpLogs.nextName(prp, "0001")), "next log serial");
        check("0001".equals(PrpPrompt.indexOf(a.file())) && PrpCreator.subFileFor(prp, "0001", 1, "notes").getFileName().toString().equals("000102-notes.md"), "indexOf, sub-file");
        check(TabName.derive(a.file(), "acme").equals("acme-1-first-new"), "short tab name: " + TabName.derive(a.file(), "acme"));
        Files.writeString(prp.resolve("001000-big.md"), "x");
        check(TabName.derive(a.file(), "acme").equals("acme-01-first-new"), "two symbols once needed: " + TabName.derive(a.file(), "acme"));
        check(TabName.projectPartOf("acme-01-first", "0001").equals("acme") && TabName.projectPartOf("acme-1-first", "0001").equals("acme"), "projectPartOf");
        var list = PrpCreator.list(prp).stream().map(PrpEntry::getIndex).toList();
        check(list.equals(List.of("0001", "0002", "000A", "0010")), "scanner, README ignored: " + list);
        System.out.println("  pass  new folder: 0001.., slot names, short tab names");
    }

    static void legacyFolder() throws Exception {
        var prp = project().resolve("prp");
        for (var i = 1; i <= 5; i++) Files.writeString(prp.resolve(String.format("%02d-prp-old_%d.md", i, i)), "x");
        var a = PrpCreator.create(prp, "first new", null, null);
        check("06".equals(a.index()) && a.file().getFileName().toString().equals("06-prp-first_new.md"), "legacy folder keeps legacy: " + a.file());
        check(!Files.exists(prp.resolve("06-prp.status.md")), "no status markdown file in a legacy folder either");
        try {
            PrpCreator.create(prp, "same number", "6", null);
            check(false, "explicit number already used");
        } catch (IllegalArgumentException expected) { }
        System.out.println("  pass  legacy folder keeps the legacy scheme");
    }

    static void legacyPastNinetyNine() throws Exception {
        var prp = project().resolve("prp");
        for (var n : List.of("98", "99")) Files.writeString(prp.resolve(n + "-prp-old_" + n + ".md"), "x");
        var a = PrpCreator.create(prp, "one", null, null);
        check("A0".equals(a.index()), "after 99: " + a.index());
        Files.writeString(prp.resolve("AH-prp-x.md"), "x");
        check("AJ".equals(PrpCreator.create(prp, "three", null, null).index()), "AH then AJ");
        var found = AgentLauncher.findPrp(prp.getParent(), "a0");
        check(found != null && found.getFileName().toString().startsWith("A0-prp-one"), "findPrp a0: " + found);
        check(PrpLogExport.pick(PrpCreator.list(prp), "ao", prp.getParent()).getIndex().equals("A0"), "logs ao -> A0");
        check("A0-prp.99.01.logs.md".equals(PrpLogs.nextName(prp, "A0")), "legacy log name");
        System.out.println("  pass  legacy past 99");
    }

    static void refusesWhenUsedUp() throws Exception {
        for (var last : List.of("ZZ-prp-last.md", "ZZZZ00-last.md")) {
            var prp = project().resolve("prp");
            Files.writeString(prp.resolve(last), "x");
            try {
                PrpCreator.create(prp, "overflow", null, null);
                check(false, "should refuse after " + last);
            } catch (IllegalStateException expected) {
                check(expected.getMessage().contains("used up"), "message: " + expected.getMessage());
            }
        }
        System.out.println("  pass  refuses after ZZ and ZZZZ");
    }

    static Path project() throws IOException {
        var dir = Files.createTempDirectory("prpnumbers");
        Files.createDirectories(dir.resolve("prp"));
        ProjFile.save(dir, new ProjJson().uuid(ProjId.mint()).name("zqnumbers").alias3("zqn").alias4("zqnm").longName("Numbers fixture"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
            } catch (IOException ignored) { }
        }));
        return dir;
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
