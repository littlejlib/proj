package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpDirFinder;
import littlejlib.proj.track.IndexCheck;
import littlejlib.proj.track.IndexMigrate;
import littlejlib.proj.track.Jsonl;
import littlejlib.proj.track.PrpIndex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Rebuilds prp/index.jsonl (a cache) from the files, reports stale lines, or migrates a hand-kept
 * 00-prp.index.md. With --depth it does the same for every prp folder below the folder.
 */
@Command(name = "index", description = "Rebuild prp/index.jsonl from the PRP files; --check reports stale lines; --migrate imports 00-prp.index.md")
public class PrpIndexCmd implements Runnable {

    @Option(names = {"-d", "--dir"}, description = "Project directory or its prp folder; with --depth any folder above projects")
    String dir;

    @Option(names = "--depth", description = "How many folder levels below the folder to look for prp folders (default 0)")
    int depth;

    @Option(names = "--check", description = "Say which lines are stale or missing, and write nothing. Exit 1 when any are")
    boolean check;

    @Option(names = "--migrate", description = "Take each PRP's summary from 00-prp.index.md into its meta.json, then rename the old file to "
            + IndexMigrate.RENAMED)
    boolean migrate;

    @Option(names = "--dry-run", description = "Say what would change, and write nothing at all (no index, no summaries, no rename)")
    boolean dryRun;

    @Option(names = "--json", description = "Print the index lines instead of a summary")
    boolean json;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var root = dir == null || dir.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(dir);
            var stale = 0;
            for (var prpDir : PrpDirFinder.find(depth == 0 ? PrpCommands.prpDir(root.toString()) : root, depth))
                stale += one(prpDir);
            if (check && stale > 0) System.exit(1);
        });
    }

    int one(Path prpDir) throws IOException {
        if (migrate && Files.isRegularFile(prpDir.resolve(IndexMigrate.OLD))) {
            var r = IndexMigrate.apply(prpDir, dryRun);
            PrpCommands.OUT.println(prpDir + "  " + (dryRun ? "would write " : "wrote ") + r.written() + " summaries, kept "
                    + r.kept() + ", " + r.notInOld().size() + " PRPs not in the old index"
                    + (r.notPrps().isEmpty() ? "" : ", old index names " + r.notPrps().size() + " numbers that are not PRPs: " + String.join(" ", r.notPrps()))
                    + "; " + r.preamble()
                    + (dryRun ? "" : "; old file renamed to " + IndexMigrate.RENAMED));
        }
        var fresh = PrpIndex.build(prpDir);
        var noUuid = IndexCheck.withoutUuid(fresh);
        if (check) {
            var stored = PrpIndex.read(prpDir);
            var problems = Files.exists(PrpIndex.file(prpDir)) ? IndexCheck.stale(stored, fresh) : List.of("no index.jsonl yet");
            problems.forEach(p -> PrpCommands.OUT.println(prpDir + "  " + p));
            return problems.size();
        }
        if (json) {
            fresh.forEach(l -> PrpCommands.OUT.println(Jsonl.GSON.toJson(l)));
        } else {
            if (!dryRun) PrpIndex.write(prpDir, fresh);
            PrpCommands.OUT.println(prpDir + "  " + (dryRun ? "would index " : "") + fresh.size() + " PRPs"
                    + (noUuid.isEmpty() ? "" : ", " + noUuid.size() + " without a uuid (proj prp uuid --backfill)"));
        }
        return 0;
    }
}
