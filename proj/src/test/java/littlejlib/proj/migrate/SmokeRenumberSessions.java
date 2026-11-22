package littlejlib.proj.migrate;

import littlejlib.proj.hib.HibRecord;
import littlejlib.proj.hib.LiveSession;
import littlejlib.proj.prp.PrpSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PRP 56 strand D: map reading, short form, tab-name rewriting, row, hibernated and tab planning, on invented data.
 *
 * mvn -q -o test-compile exec:java -Dexec.mainClass="littlejlib.proj.migrate.SmokeRenumberSessions" -Dexec.classpathScope=test
 */
public class SmokeRenumberSessions {

    public static void main(String[] args) throws Exception {
        shortForm();
        tabNames();
        var prp = Files.createDirectories(Files.createTempDirectory("smokerenum").resolve("billing").resolve("prp"));
        var map = FolderMap.parse(prp, mapLines(40));
        rows(map, prp);
        hib(map, prp);
        tabs(map, prp);
        widthOne(prp);
        find(prp);
        System.out.println("ok");
        System.exit(0);
    }

    static List<String> mapLines(int count) {
        var lines = new ArrayList<String>();
        for (var n = 1; n <= count; n++) {
            var old = String.format("%02d", n);
            var neu = ShortNumber.of(base34(n), 4);
            lines.add("{\"oldFile\":\"" + old + "-prp-task" + n + ".md\",\"newFile\":\"" + neu + "00-task" + n
                    + ".md\",\"oldNumber\":\"" + old + "\",\"newNumber\":\"" + neu + "\",\"kind\":\"main\",\"uuid\":\"u" + n + "\"}");
            lines.add("{\"oldFile\":\"" + old + "-prp.99.01.logs.md\",\"newFile\":\"" + neu + "ZZ-01.logs.md\",\"oldNumber\":\""
                    + old + "\",\"newNumber\":\"" + neu + "\",\"kind\":\"log\",\"uuid\":\"u" + n + "\"}");
        }
        return lines;
    }

    static String base34(int n) {
        var s = new StringBuilder();
        for (; n > 0; n /= 34) s.append(ShortNumber.DIGITS.charAt(n % 34));
        return s.reverse().toString();
    }

    static void shortForm() {
        check(ShortNumber.stripped("0001").equals("1") && ShortNumber.stripped("000A").equals("A"), "stripped");
        check(ShortNumber.stripped("0000").equals("0"), "zero");
        check(ShortNumber.value("0010") == 34 && ShortNumber.value("00o1") == 1 + 0, "value, o reads as 0");
        check(ShortNumber.width(List.of("0001", "0009")) == 1, "width 1 up to 9");
        check(ShortNumber.width(List.of("0001", "000Z")) == 1, "width 1 up to Z");
        check(ShortNumber.width(List.of("0001", "0010")) == 2, "width 2 from 34 on");
        check(ShortNumber.width(List.of()) == 1, "width of nothing is 1");
        check(ShortNumber.of("0001", 2).equals("01") && ShortNumber.of("000A", 2).equals("0A") && ShortNumber.of("0001", 1).equals("1"), "padded to width");
        check(ShortNumber.of("0010", 1).equals("10"), "never cut below the number's own length");
        System.out.println("  pass  short form: base34 value, width, padding");
    }

    static void tabNames() {
        check(Rewriter.tab("billing-38-r2", "38", "26").equals("billing-26-r2"), "middle");
        check(Rewriter.tab("billing-38", "38", "1").equals("billing-1"), "trailing");
        check(Rewriter.tab("AB-CD-03-yt-notice", "03", "3").equals("AB-CD-3-yt-notice"), "key with hyphens, upper case");
        check(Rewriter.tab("billing-A0-x", "a0", "2Y").equals("billing-2Y-x"), "case-insensitive old number");
        check(Rewriter.tab("billing-x", "38", "1").equals("billing-x"), "no old number: unchanged");
        check(Rewriter.tab("38-task", "38", "1").equals("38-task"), "number at the very start is not a tab number");
        check(Rewriter.tab("billing-38-r2-38-x", "38", "9").equals("billing-9-r2-38-x"), "first only");
        check(Rewriter.tab(null, "38", "9") == null, "null kept");
        System.out.println("  pass  tab names: middle, trailing, hyphenated key, case, unchanged, first only");
    }

    static PrpSession row(Path prp, String id, String file, String index, String tab) {
        return new PrpSession().sessionId(id).prpFile(prp.resolve(file).toString()).prpIndex(index).tabName(tab);
    }

    static void rows(FolderMap map, Path prp) {
        var rw = new Rewriter(map);
        var m = rw.forRow(row(prp, "s1", "05-prp-task5.md", "05", "billing-05-task"));
        check(m != null && m.toFile().equals(prp.resolve("0005" + "00-task5.md").toString()), "new file path");
        check(m.toIndex().equals("0005") && m.toName().equals("billing-05-task") && m.how().equals("file"), "index, name (width 2, same text), how");
        var big = rw.forRow(row(prp, "s2", "36-prp-task36.md", "36", "billing-36-x"));
        check(big.toIndex().equals("0012") && big.toName().equals("billing-12-x"), "36th PRP is number 12 in base34 (36 = 1*34+2)");
        var closed = rw.forRow(row(prp, "s3", "07-prp-renamed_later.closed.md", "07", "billing-07-x"));
        check(closed != null && closed.how().equals("number") && closed.toFile().endsWith("0007" + "00-task7.md"), "file renamed before: matched by number");
        check(rw.forRow(row(prp, "s4", "0005" + "00-task5.md", "0005", "billing-05-task")) == null, "already new: left alone");
        check(rw.forRow(row(prp.getParent().resolve("other"), "s5", "05-prp-task5.md", "05", "x-05-y")) == null, "another folder: left alone");
        check(rw.forRow(new PrpSession().sessionId("s6")) == null, "no PRP file: left alone");
        check(rw.forRow(row(prp, "s7", "50-prp-none.md", "50", "billing-50-x")) == null, "a PRP the map does not know: left alone");
        var plain = rw.forRow(row(prp, "s8", "05-prp-task5.md", "05", "my own name"));
        check(plain.changed() && plain.toName().equals("my own name"), "free-form name kept, file still moves");
        System.out.println("  pass  rows: file, number fallback, new, other folder, unknown");
    }

    static void hib(FolderMap map, Path prp) {
        var rw = new Rewriter(map);
        var r = new HibRecord().sessionId("h1").name("billing-10-api").prpFile(prp.resolve("10-prp-task10.md").toString())
                .prpIndex("10").logFile(prp.resolve("10-prp.99.01.logs.md").toString());
        var m = rw.forHib(r);
        check(m.toName().equals("billing-0A-api") && m.toIndex().equals("000A"), "name and number");
        check(m.toLog().equals(prp.resolve("000A" + "ZZ-01.logs.md").toString()), "log file moved by the map");
        HibRows.set(r, m);
        check(r.prpFile().endsWith("000A" + "00-task10.md") && r.name().equals("billing-0A-api") && r.logFile().endsWith("ZZ-01.logs.md"), "record rewritten");
        var loose = new HibRecord().sessionId("h2").name("x");
        check(rw.forHib(loose) == null, "no PRP: left alone");
        var noName = new HibRecord().sessionId("h3").prpFile(prp.resolve("10-prp-task10.md").toString()).prpIndex("10");
        HibRows.set(noName, rw.forHib(noName));
        check(noName.name() == null, "no name stays none");
        check(HibRows.plan(rw, List.of(r, loose)).isEmpty(), "idempotent: planned again after the rewrite, nothing");
        System.out.println("  pass  hibernated: name, number, log, loose, idempotent");
    }

    static TabInfo tab(String id, String title, String folder, String session) {
        return new TabInfo().id(id).title(title).folder(folder).live(session != null).session(session);
    }

    static void tabs(FolderMap map, Path prp) {
        var rw = new Rewriter(map);
        var moves = new ArrayList<Move>();
        moves.add(rw.forRow(row(prp, "s1", "10-prp-task10.md", "10", "billing-10-api")));
        var project = map.project().toString();
        var list = List.of(
                tab("t1", "billing-10-api", project, null),
                tab("t2", "\u2733 billing-10-api", project.replace('\\', '/'), "s1"),
                tab("t3", "billing-10-api", project + "-elsewhere", null),
                tab("t4", "billing-11-old", project, null),
                tab("t5", "Claude's own summary", project, "s1"),
                tab("t6", "billing-0A-api", project, null));
        var renames = TabMatch.renames(map, list, moves);
        check(renames.equals(Map.of("t1", "billing-0A-api", "t2", "billing-0A-api")), "exact old name, status mark ignored, own folder only: " + renames);
        var unmatched = TabMatch.unmatched(map, list, renames).stream().map(TabInfo::id).toList();
        check(unmatched.equals(List.of("t4")), "old-looking name without a record is reported only: " + unmatched);
        var other = TabMatch.byOtherTitle(map, list, moves, renames).stream().map(TabInfo::id).toList();
        check(other.equals(List.of("t5")), "moved session under another title is reported: " + other);
        var live = new LiveSession().pid(1L).sessionId("s1").cwd(project);
        var far = new LiveSession().pid(2L).sessionId("z").cwd(project + "-elsewhere");
        check(TabMatch.running(map, List.of(live, far)).size() == 1, "running sessions of the folder");
        var parsed = parse();
        check(parsed.size() == 2 && parsed.get(1).session().equals("abc") && parsed.get(0).folder().equals("D:\\work\\a"), "ctl list parsed, group labels skipped");
        System.out.println("  pass  tabs: renames, unmatched, other title, running, ctl list parsing");
    }

    static List<TabInfo> parse() {
        var json = "{\"ok\":true,\"windows\":[{\"index\":0,\"tabs\":[{\"id\":\"g\",\"title\":\"Group\",\"separator\":true},"
                + "{\"id\":\"a1\",\"title\":\"x-1-y\",\"folder\":\"D:\\\\work\\\\a\",\"live\":false},"
                + "{\"id\":\"b2\",\"title\":\"x-2-y\",\"folder\":\"D:\\\\work\\\\b\",\"live\":true,\"claude\":{\"session\":\"abc\",\"status\":\"idle\"}}]}]}";
        try {
            return TabsCtl.parse(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static void widthOne(Path prp) throws Exception {
        var small = FolderMap.parse(prp, mapLines(9));
        check(small.width() == 1, "9 PRPs: width 1");
        var m = new Rewriter(small).forRow(row(prp, "s1", "09-prp-task9.md", "09", "billing-09-z"));
        check(m.toName().equals("billing-9-z"), "short form without padding: " + m.toName());
        var bad = false;
        try {
            FolderMap.parse(prp, List.of("{\"oldFile\":\"x\"}"));
        } catch (IllegalArgumentException e) {
            bad = true;
        }
        check(bad, "a map line without a new name is refused");
        System.out.println("  pass  width one and a bad map");
    }

    static void find(Path prp) throws Exception {
        var root = prp.getParent().getParent();
        Files.writeString(prp.resolve(FolderMap.FILE), "");
        var git = Files.createDirectories(root.resolve("x").resolve(".git").resolve("prp"));
        Files.writeString(git.resolve(FolderMap.FILE), "");
        var found = FolderMap.find(root, 3);
        check(found.equals(List.of(prp)), "found the folder, skipped .git: " + found);
        check(FolderMap.find(root, 1).isEmpty(), "depth limits the search");
        System.out.println("  pass  find: depth and skipped folders");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAIL " + what);
    }
}
