package littlejlib.proj.prp;

import java.nio.file.Path;

/**
 * The key offered for a folder: no date, no filler words.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.prp.SmokeShortName" -Dexec.classpathScope=test [-Dexec.args="folder ..."]
 */
public class SmokeShortName {

    public static void main(String[] args) {
        if (args.length > 0) {
            for (var a : args) System.out.println(TabName.shortenProject(Path.of(a)) + "\t" + TabName.withoutDate(Path.of(a).getFileName().toString()));
            System.exit(0);
        }
        same("2026-02-25 THE Acme Bio", "acmebio");
        same("1500-00-00 to 2000-00-00 old ledgers of the town", "oldledgers");
        same("2026-06-social-media", "socialmedia");
        same("exporter 2026-11", "exporter");
        same("billing", "billing");
        same("2026-10-17 a customer came by and asked for a refund", "customercame");
        same("2026-10-21 Invoice email with treaty", "invoiceemail");
        same("2026-10-21 Statements archive", "statements");
        same("2026-11-13", "20261113");
        System.out.println("  pass  short names: dates and filler words left out");
        System.out.println("ok");
        System.exit(0);
    }

    static void same(String folder, String expected) {
        var got = TabName.shortenProject(Path.of(folder));
        if (!got.equals(expected)) throw new AssertionError(folder + " -> " + got + ", expected " + expected);
    }
}
