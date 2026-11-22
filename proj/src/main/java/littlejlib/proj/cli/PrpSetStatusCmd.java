package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpHead;
import littlejlib.proj.prp.PrpState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Sets {@code prp_status} in a PRP's head. Closing a PRP is this, never a rename. */
@Command(name = "set-status", description = {"Set a PRP's status in its head (prp_status): new, in_progress, in_review, on_hold, blocked, closed, cancelled.",
        "Writes through notepadfx when the file is open there, so the person's unsaved typing is safe."})
public class PrpSetStatusCmd implements Runnable {

    @Parameters(index = "0", description = "PRP index")
    String index;

    @Parameters(index = "1", description = "One of: new, in_progress, in_review, on_hold, blocked, closed, cancelled")
    String value;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var state = PrpState.require(value);
            var prpDir = PrpCommands.prpDir(dir);
            var entry = PrpCommands.resolve(prpDir, index);
            PrpHead.setStatus(entry.getMainFile(), state);
            PrpCommands.OUT.println(entry.getIndex() + "  prp_status: " + state.key());
        });
    }
}
