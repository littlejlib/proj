package littlejlib.proj.prp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/**
 * proj.json without the index or the network: the uuid, the file round trip, the origin parse and
 * the renaming of a remembered tab.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.prp.SmokeProjJson" -Dexec.classpathScope=test
 */
public class SmokeProjJson {

    public static void main(String[] args) throws Exception {
        var seen = new HashSet<String>();
        for (var i = 0; i < 1000; i++) {
            var id = ProjId.mint();
            if (!ProjId.valid(id)) throw new AssertionError("uuid: " + id);
            if (!seen.add(id)) throw new AssertionError("uuid repeated");
        }

        System.out.println("  pass  1000 uuids valid and distinct");
        var dir = Files.createTempDirectory("projjson");
        try {
            check(ProjFile.read(dir) == null, "no file, no value");
            ProjFile.save(dir, new ProjJson().uuid(ProjId.mint()).name("billing").longName("Billing service"));
            var back = ProjFile.read(dir);
            check("billing".equals(back.name()) && "Billing service".equals(back.longName()), "round trip");
            check(back.icon() == null, "an unset icon stays unset");
            check(!Files.readString(ProjFile.file(dir)).contains("icon"), "nulls are not written");
            check("billing".equals(ProjFile.nameOf(dir)), "nameOf");

            Files.createDirectories(dir.resolve(".git"));
            Files.writeString(dir.resolve(".git").resolve("config"), String.join("\n",
                    "[core]", "\turl = nothing", "[remote \"upstream\"]", "\turl = https://github.com/other/x.git",
                    "[remote \"origin\"]", "\turl = git@github.com:acme/billing.git", ""));
            check("acme".equals(GitOrigin.org(dir)), "ssh origin -> " + GitOrigin.org(dir));
            Files.writeString(dir.resolve(".git").resolve("config"),
                    "[remote \"origin\"]\n\turl = https://github.com/acme2/billing.git\n");
            check("acme2".equals(GitOrigin.org(dir)), "https origin");
            Files.writeString(dir.resolve(".git").resolve("config"),
                    "[remote \"origin\"]\n\turl = https://example.org/a/b.git\n");
            check(GitOrigin.org(dir) == null, "not github");

            check(ProjIcon.current(dir, back) == null, "no icon anywhere");
            Files.writeString(dir.resolve("logo.png"), "x");
            check(ProjIcon.current(dir, back.icon("logo.png")) != null, "relative file is read where it is");
        } finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
            }
        }

        check("prjmgmtool-01-say".equals(PrpSessionStore.rekey("old-01-say", "01", "prjmgmtool")), "rekey");
        check("old-1-say".equals(PrpSessionStore.rekey("old-1-say", "01", "new")), "no marker, unchanged");
        System.out.println("ok");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("  pass  " + what);
    }
}
