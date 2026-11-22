package littlejlib.proj.prp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/**
 * PRP 55 piece 1: base34 ids and the uuid line of a PRP file.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.prp.SmokePrpUuid" -Dexec.classpathScope=test
 */
public class SmokePrpUuid {

    public static void main(String[] args) throws Exception {
        ids();
        line();
        files();
        System.out.println("ok");
        System.exit(0);
    }

    static void ids() {
        var seen = new HashSet<String>();
        for (var i = 0; i < 2000; i++) {
            var id = Base34.mint();
            check(Base34.valid(id) && id.length() == 26 && seen.add(id), "mint " + id);
            check(id.indexOf('i') < 0 && id.indexOf('o') < 0, "no i or o " + id);
            check(ProjId.valid(ProjId.mint()), "project id");
        }
        check(ProjId.valid("abcdefghijklmnopqrstuvwxyzab") && ProjId.legacy("abcdefghijklmnopqrstuvwxyzab"), "legacy base26 stays valid");
        check(!ProjId.valid("short") && !ProjId.valid(null), "junk refused");
        check(Base34.normalize(" 0I2O4A ").equals("01204a") && Base34.validPrefix("A0Io"), "typed form");
        check(!Base34.validPrefix("") && !Base34.validPrefix("a-b"), "bad prefix");
        System.out.println("  pass  ids: 26 symbols, no i/o, distinct, legacy project ids still valid");
    }

    static void line() {
        var u = Base34.mint();
        var text = "- PRP Number: 07\n- PRP Name: x\n- Usage Guide:\n\t- a\n";
        var out = PrpUuid.with(text, u);
        check(out.equals("- PRP Number: 07\n- PRP Name: x\n- PRP UUID: " + u + "\n- Usage Guide:\n\t- a\n"), "inserted under the name: " + out);
        check(PrpUuid.with(out, Base34.mint()).equals(out), "existing line untouched");
        var crlf = PrpUuid.with("- PRP Name: x\r\nrest\r\n", u);
        check(crlf.equals("- PRP Name: x\r\n- PRP UUID: " + u + "\r\nrest\r\n"), "crlf kept");
        check(PrpUuid.with("no header\n", u).startsWith("- PRP UUID: " + u + "\n"), "no header: first line");
        check(PrpUuid.with("- PRP Name: x\n- PRP UUID: \n", u).contains(u), "empty value filled");
        System.out.println("  pass  line: placement, idempotent, CRLF kept");
    }

    static void files() throws Exception {
        var dir = Files.createTempDirectory("smokeuuid");
        var prp = Files.createDirectories(dir.resolve("proj1").resolve("prp"));
        var file = prp.resolve("01-prp-thing.md");
        Files.writeString(file, "- PRP Number: 01\n- PRP Name: thing\n\n# Requirement\nbody\n");
        var first = PrpUuid.ensure(file, false);
        check(first.inserted() && Base34.valid(first.uuid()), "inserted");
        var again = PrpUuid.ensure(file, false);
        check(!again.inserted() && again.uuid().equals(first.uuid()), "second call changes nothing");
        var moved = Files.move(file, prp.resolve("01-prp-thing.closed.md"));
        check(first.uuid().equals(PrpUuid.read(moved)), "uuid survives rename");
        var found = PrpDirFinder.find(dir, 0);
        check(found.isEmpty(), "depth 0 finds no prp folder above the project: " + found);
        found = PrpDirFinder.find(dir, 1);
        check(found.size() == 1 && found.get(0).equals(prp.toAbsolutePath().normalize()), "depth 1 finds it: " + found);
        System.out.println("  pass  files: ensure, rename, folder finder");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
