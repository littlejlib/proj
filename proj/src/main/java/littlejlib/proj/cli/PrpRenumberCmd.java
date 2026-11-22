package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpDirFinder;
import littlejlib.proj.renumber.Renumber;
import littlejlib.proj.renumber.RenumberResult;
import littlejlib.proj.renumber.RenumberRun;
import littlejlib.proj.renumber.RenumberTotals;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

import static littlejlib.proj.cli.PrpCommands.OUT;

/** Moves prp folders from the legacy numbering (NN-prp-name.md) to the four-symbol one (NNNN00-name.md). */
@Command(name = "renumber", description = "Renumber prp folders to the four-symbol base34 scheme: map, rename, rewrite references, record was")
public class PrpRenumberCmd implements Runnable {

    @Option(names = {"-d", "--dir"}, description = "Project directory or its prp folder; with --depth any folder above projects")
    String dir;

    @Option(names = "--depth", description = "How many folder levels below the folder to look for prp folders (default 0)")
    int depth;

    @Option(names = "--dry-run", description = "Print the mapping and the review list, and change nothing at all")
    boolean dryRun;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var root = dir == null || dir.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(dir);
            var results = RenumberRun.run(PrpDirFinder.find(depth == 0 ? PrpCommands.prpDir(root.toString()) : root, depth), dryRun);
            results.forEach(this::print);
            RenumberTotals.lines(results, dryRun).forEach(OUT::println);
            var reviews = results.stream().mapToInt(r -> r.reviews().size()).sum();
            var refused = results.stream().filter(r -> r.state().equals("refused")).count();
            if (!dryRun && reviews > 0) OUT.println("review items are in each folder's prp/" + Renumber.REVIEW);
            if (refused > 0) System.exit(1);
        });
    }

    RenumberResult print(RenumberResult r) {
        OUT.println(r.folder() + "  " + r.state() + (r.why() == null ? "" : ": " + r.why()));
        if (r.plan() == null) return r;
        r.plan().notes().forEach(n -> OUT.println("  note  " + n));
        r.plan().files().forEach(f -> OUT.println("  " + f.oldFile() + "  ->  " + f.newFile()));
        r.rewritten().forEach(f -> OUT.println("  " + (dryRun ? "would rewrite " : "rewrote ") + f));
        r.reviews().forEach(v -> OUT.println("  review  " + v.file() + ":" + v.line() + "  " + v.why() + "  |  " + v.text()));
        return r;
    }
}
