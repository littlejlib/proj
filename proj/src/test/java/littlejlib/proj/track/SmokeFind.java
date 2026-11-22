package littlejlib.proj.track;

import littlejlib.proj.prp.PrpUuid;

import java.nio.file.Files;
import java.util.List;

/**
 * PRP 55 piece 3: finding a PRP across projects, on invented folders; a moved and renamed PRP is
 * found again by its uuid once the cache is rebuilt.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.track.SmokeFind" -Dexec.classpathScope=test
 */
public class SmokeFind {

    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("smokefind");
        var billing = Files.createDirectories(root.resolve("acme").resolve("billing").resolve("prp"));
        var ledger = Files.createDirectories(root.resolve("acme").resolve("ledger").resolve("prp"));
        Files.writeString(billing.resolve("07-prp-csv_export.md"), "- PRP Number: 07\n- PRP Name: csv export\n");
        Files.writeString(billing.resolve("08-prp-db_backup.closed.md"), "- PRP Number: 08\n- PRP Name: db backup\n");
        Files.writeString(ledger.resolve("07-prp-monthly_totals.md"), "- PRP Number: 07\n- PRP Name: monthly totals\n");
        var u = PrpUuid.ensure(billing.resolve("07-prp-csv_export.md"), false).uuid();
        MetaFile.update(billing, "07", u, m -> m.summary("Invoices to CSV for the accountant"));
        var roots = List.of(new GlobalIndex.Root(root, 2));
        var all = GlobalIndex.build(roots);
        check(all.size() == 3, "three PRPs in two projects: " + all.size());

        check(one(GlobalFind.find(all, u.substring(0, 6))).file().endsWith("07-prp-csv_export.md"), "uuid prefix");
        check(one(GlobalFind.find(all, u.toUpperCase())).uuid().equals(u), "uuid in any case");
        check(GlobalFind.find(all, "07").size() == 2, "a bare number matches in every project");
        check(one(GlobalFind.find(all, "ledger-07")).name().equals("monthly_totals"), "folder name as key");
        check(one(GlobalFind.find(all, "accountant csv")).number().equals("07"), "words in the summary");
        check(one(GlobalFind.find(all, "db")).number().equals("08"), "a word that parses as a number stays a word");
        check(GlobalFind.find(all, "nothing here").isEmpty(), "no hit");

        var moved = Files.createDirectories(root.resolve("acme").resolve("reports").resolve("prp"));
        Files.move(billing.resolve("07-prp-csv_export.md"), moved.resolve("03-prp-csv_export_for_accounts.md"));
        var stale = one(GlobalFind.find(all, u.substring(0, 8)));
        check(!GlobalFind.stillThere(stale), "the cached place no longer holds it");
        var found = one(GlobalFind.find(GlobalIndex.build(roots), u.substring(0, 8)));
        check(found.file().endsWith("03-prp-csv_export_for_accounts.md") && found.project().endsWith("reports"), "found where it moved: " + found.file());
        check(GlobalFind.stillThere(found), "and it is there");
        System.out.println("  pass  find: uuid, ticket, number, words; a moved and renamed PRP is found by its uuid");
        System.out.println("ok");
        System.exit(0);
    }

    static GlobalLine one(List<GlobalLine> hits) {
        check(hits.size() == 1, "expected one hit, got " + hits.size());
        return hits.get(0);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
