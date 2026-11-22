package littlejlib.proj.renumber;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static littlejlib.proj.renumber.SmokeRenumber.check;
import static littlejlib.proj.renumber.SmokeRenumber.write;

/** PRP 56: one run over two folders that name each other's files, a later run reading an earlier map, and the totals. */
public class SmokeRenumberRun {

    static void twoFolders(Path billing) throws Exception {
        var root = billing.getParent().getParent();
        var ledger = Files.createDirectories(root.resolve("ledger").resolve("prp"));
        write(ledger, "01-prp-intake.md", "- PRP Number: 01\n");
        write(ledger, "01-prp-intake_old.closed.md", "the first try\n");
        write(ledger, "02-prp-report.closed.md", "older report\n");
        write(ledger, "02-prp-report_draft.closed.md", "newer report\n");
        write(ledger, "02-prp.01.notes.md", "notes\n");
        write(ledger, "01-prp.status.md", String.join("\n", "Uses " + billing.resolve("01-prp-setup_db.md"),
                "and billing/prp/34-prp-ui.md, see billing PRP 10.", ""));
        var results = RenumberRun.run(List.of(billing, ledger), false);
        check(results.stream().allMatch(r -> r.state().equals("done")), "both done: " + results.stream().map(r -> r.state() + " " + r.why()).toList());
        for (var e : List.of("000100-intake.md", "000102-intake_old.closed.md", "000200-report_draft.closed.md", "000202-notes.md", "000203-report.closed.md"))
            check(Files.exists(ledger.resolve(e)), "ledger lacks " + e);
        var api = Files.readString(billing.resolve("000300-api.md"));
        check(api.contains("Ledger: ledger/prp/000202-notes.md and ../ledger/prp/000100-intake.md"), "billing names ledger files:\n" + api);
        var status = Files.readString(ledger.resolve("000101.status.md"));
        check(status.contains(billing.resolve("000100-setup_db.md").toString()) && status.contains("billing/prp/000400-ui.md")
                && status.contains("billing PRP 0003."), "ledger names billing files and PRPs:\n" + status);
        var totals = RenumberTotals.lines(results, false);
        check(totals.get(0).startsWith("totals: 2 folders (2 done), 23 renames, ") && totals.get(0).contains(" review items"), "totals " + totals);
        check(totals.stream().anyMatch(l -> l.matches("  review  \\d+  names a file in another folder")), "reasons " + totals);

        var later = Files.createDirectories(root.resolve("acme").resolve("prp"));
        write(later, "01-prp-x.md", "see ../ledger/prp/02-prp.01.notes.md\n");
        var r = RenumberRun.run(List.of(later), false).get(0);
        check(r.state().equals("done") && Files.readString(later.resolve("000100-x.md")).equals("see ../ledger/prp/000202-notes.md\n"),
                "a later run reads the earlier map: " + Files.readString(later.resolve("000100-x.md")));
        System.out.println("  pass  two folders in one run, a later run against an earlier map, totals");
    }
}
