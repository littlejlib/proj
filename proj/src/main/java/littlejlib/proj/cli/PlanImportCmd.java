package littlejlib.proj.cli;

import littlejlib.proj.track.Workplan;
import littlejlib.proj.track.WorkplanImport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

/** Brings an older work-plan jsonl ("0001" ids, rows changed in place) into an empty prp/workplan.jsonl. */
@Command(name = "import", description = "Import an older work-plan jsonl into an empty prp/workplan.jsonl")
public class PlanImportCmd implements Runnable {

    @Parameters(index = "0", description = "The jsonl file to import (it is only read)")
    Path from;

    @Option(names = {"-p", "--prp"}, required = true, description = "The owning PRP of items that name none (number or uuid)")
    String prp;

    @Option(names = "--dry-run", description = "Check and count, write nothing")
    boolean dryRun;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var items = WorkplanImport.run(from, prpDir, TrackCli.target(prpDir, prp), dryRun);
            PrpCommands.OUT.println((dryRun ? "would import " : "imported ") + items.size() + " items into "
                    + Workplan.file(prpDir));
        });
    }
}
