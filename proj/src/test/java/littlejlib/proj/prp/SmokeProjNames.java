package littlejlib.proj.prp;

import littlejlib.proj.track.GlobalFind;
import littlejlib.proj.track.GlobalLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * PRP 56 decision 7: the four names of a project, on invented fixtures in temp folders.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.prp.SmokeProjNames" -Dexec.classpathScope=test
 */
public class SmokeProjNames {

    public static void main(String[] args) throws Exception {
        validation();
        refusal();
        success();
        oldProjJson();
        aliasTicket();
        System.out.println("ok");
        System.exit(0);
    }

    static void validation() {
        var none = (java.util.function.Function<String, String>) k -> null;
        var good = new ProjJson().name("Zq Ledger").alias3("ZQL").alias4("zqlg").longName("Ledger fixture");
        check(ProjNames.check(good, none, true).isEmpty(), "valid names");
        check(ProjNames.normalize(good).name().equals("zqledger") && "zql".equals(ProjNames.normalize(good).alias3()), "stored lower case");
        check(has(ProjNames.check(new ProjJson().alias3("zql").alias4("zqlg").longName("x"), none, true), "short name"), "short name required");
        check(has(ProjNames.check(good.alias3("zq"), none, true), "alias3"), "alias3 too short");
        check(has(ProjNames.check(good.alias3("zql").alias4("zqlgg"), none, true), "alias4"), "alias4 too long");
        check(has(ProjNames.check(good.alias4("zq-g"), none, true), "alias4"), "alias4 has a dash");
        check(has(ProjNames.check(good.alias4("zqlg").longName(" "), none, true), "full name"), "full name blank");
        check(has(ProjNames.check(good.longName("x"), k -> k.equals("zql") ? "/other" : null, true), "already the short name of /other"), "alias clash with another short name");
        check(ProjNames.check(new ProjJson().name("zqledger"), none, false).isEmpty(), "old file: aliases not required");
        check(has(ProjNames.check(new ProjJson().name("zqledger").alias3("a"), none, false), "alias3"), "old file: a given alias is checked");
    }

    static void refusal() throws Exception {
        var project = project("zqrefuse");
        var prp = project.resolve("prp");
        try {
            PrpCreator.create(prp, "first one", null, null);
            check(false, "should refuse without names");
        } catch (IllegalArgumentException e) {
            var m = e.getMessage();
            check(m.contains("--short") && m.contains("--alias3") && m.contains("--alias4") && m.contains("--full-name"), "message lists the four options");
            check(m.contains("--alias3 zqr") && m.contains("--alias4 zqre"), "message suggests values from the folder name");
        }
        check(!Files.exists(ProjFile.file(project)) && noPrpFiles(prp), "nothing written on refusal");
        try {
            PrpCreator.create(prp, "first one", null, null, false, new ProjJson().name("zqrefuse").alias3("zqr").alias4("zq").longName("x"));
            check(false, "should refuse a bad alias4");
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains("alias4: exactly 4"), "bad alias4 named");
        }
        check(noPrpFiles(prp), "still nothing written");
    }

    static void success() throws Exception {
        var project = project("zqsuccess");
        var prp = project.resolve("prp");
        var names = new ProjJson().name("Zq-Success").alias3("ZQS").alias4("zqsc").longName("Success fixture");
        var created = PrpCreator.create(prp, "first one", null, null, false, names);
        check(Files.isRegularFile(created.file()), "PRP written");
        var p = ProjFile.read(project);
        check("zqsuccess".equals(p.name()) && "zqs".equals(p.alias3()) && "zqsc".equals(p.alias4()) && "Success fixture".equals(p.longName()), "proj.json holds the four names");
        check(ProjId.valid(p.uuid()), "proj.json has a uuid");
        check(ProjNames.hint(prp) == null, "no hint when aliases exist");
        var second = PrpCreator.create(prp, "second one", null, null);
        check(Files.isRegularFile(second.file()), "second PRP needs no names");
    }

    static void oldProjJson() throws Exception {
        var project = project("zqold");
        var prp = project.resolve("prp");
        ProjFile.save(project, new ProjJson().uuid(ProjId.mint()).name("zqold").longName("Old fixture"));
        var created = PrpCreator.create(prp, "still allowed", null, null);
        check(Files.isRegularFile(created.file()), "old proj.json: PRP created with no names given");
        var hint = ProjNames.hint(prp);
        check(hint != null && hint.startsWith("hint:") && !hint.contains("\n") && hint.contains("--alias3"), "one-line hint: " + hint);
        check(ProjFile.read(project).alias3() == null, "old file untouched");
    }

    static void aliasTicket() {
        var a = new GlobalLine().project("/x/ledger").key("zqledger").alias3("zql").alias4("zqlg").number("07").uuid("").name("n").file("/x/ledger/prp/07-prp-n.md");
        var b = new GlobalLine().project("/x/other").key("zqother").alias3("zqo").alias4("zqot").number("07").uuid("").name("m").file("/x/other/prp/07-prp-m.md");
        var all = List.of(a, b);
        check(GlobalFind.find(all, "zqledger-7").equals(List.of(a)), "ticket by short name");
        check(GlobalFind.find(all, "zql-7").equals(List.of(a)), "ticket by alias3");
        check(GlobalFind.find(all, "ZQLG-07").equals(List.of(a)), "ticket by alias4, any case");
        check(GlobalFind.find(all, "zqo-07").equals(List.of(b)), "other project's alias");
        check(GlobalFind.find(all, "7").size() == 2, "bare number still finds both");
    }

    static Path project(String name) throws Exception {
        var dir = Files.createTempDirectory(name);
        Files.createDirectories(dir.resolve("prp"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
            } catch (Exception ignored) { }
        }));
        return dir;
    }

    static boolean noPrpFiles(Path prp) throws Exception {
        try (var files = Files.list(prp)) {
            return files.findAny().isEmpty();
        }
    }

    static boolean has(List<String> errors, String text) {
        return errors.stream().anyMatch(e -> e.contains(text));
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("  pass  " + what);
    }
}
