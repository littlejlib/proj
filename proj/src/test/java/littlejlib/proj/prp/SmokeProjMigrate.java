package littlejlib.proj.prp;

import java.nio.file.Files;

/**
 * A project that has a key in the index and no proj.json gets that key written into the file, and a
 * second project asking for the same name is told it is taken. Uses the real index, so run it with
 * no daemon up; it leaves one row for a folder it deletes, which lookups skip.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.prp.SmokeProjMigrate" -Dexec.classpathScope=test
 */
public class SmokeProjMigrate {

    public static void main(String[] args) throws Exception {
        var a = Files.createTempDirectory("migrate-a");
        var b = Files.createTempDirectory("migrate-b");
        try {
            PrpSessions.cacheKey(a, "oldkey");
            System.out.println("  index says " + PrpSessions.dbKey(a) + " for " + a);
            check("oldkey".equals(ProjFile.ensure(a).name()), "index key carried into proj.json");
            check("oldkey".equals(ProjFile.nameOf(a)), "and it is on disk");
            check(a.toString().equals(PrpSessions.keyOwner("oldkey", b)), "b is told a owns it");
            check(PrpSessions.keyOwner("oldkey", a) == null, "a does not clash with itself");
            ProjFile.update(b, p -> p.name("oldkey"));
            check("oldkey".equals(ProjFile.nameOf(b)), "a typed duplicate is still allowed");
        } finally {
            for (var d : new java.nio.file.Path[] {a, b}) {
                Files.deleteIfExists(ProjFile.file(d));
                Files.deleteIfExists(d.resolve("prp"));
                Files.deleteIfExists(d);
            }
        }
        System.out.println("ok");
        System.exit(0);   // the index's server threads would otherwise keep the process alive
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("  pass  " + what);
    }
}
