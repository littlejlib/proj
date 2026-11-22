package littlejlib.proj.cli;

import littlejlib.proj.track.Budget;
import littlejlib.proj.track.Jsonl;
import littlejlib.proj.track.MetaFile;
import littlejlib.proj.track.PrpRefs;
import littlejlib.proj.track.Timeline;
import littlejlib.proj.track.Views;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Where a PRP stands: its state, summary, relations and its latest notes, within a budget. */
@Command(name = "status", aliases = "st", description = "Show a PRP's state and latest timeline notes")
public class PrpStatusCmd implements Runnable {

    @Parameters(arity = "0..1", description = "PRP index. Defaults to the PRP this session opened on")
    String index;

    @Option(names = "--all", description = "Every note, not only the newest " + Views.LAST_NOTES)
    boolean all;

    @Option(names = "--budget", description = "Stop at about this many tokens (characters / 4) and count what was left out")
    long budget;

    @Option(names = "--json", description = "The timeline lines as they are stored")
    boolean json;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var entry = PrpCommands.resolve(prpDir, index);
            var notes = Timeline.read(prpDir, entry.getIndex());
            if (json) {
                notes.forEach(n -> PrpCommands.OUT.println(Jsonl.GSON.toJson(n)));
                return;
            }
            var b = new Budget(PrpCommands.OUT, budget);
            Views.timeline(b, entry, TrackCli.uuid(entry), MetaFile.read(prpDir, entry.getIndex()), notes, all, PrpRefs.of(prpDir));
            if (notes.isEmpty() && entry.getStatusFile() != null) b.line("(no timeline yet; the older notes are in " + entry.getStatusFile().getFileName() + ")");
            b.end();
        });
    }
}
