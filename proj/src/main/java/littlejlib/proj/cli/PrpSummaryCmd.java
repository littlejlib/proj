package littlejlib.proj.cli;

import littlejlib.proj.track.MetaFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

/** The one-line summary of a PRP, kept in NN-prp.meta.json (never in the PRP file). */
@Command(name = "summary", description = "Show or set a PRP's one-line summary (NN-prp.meta.json)")
public class PrpSummaryCmd implements Runnable {

    @Parameters(index = "0", description = "PRP index")
    String index;

    @Parameters(index = "1..*", arity = "0..*", description = "The summary, at most " + MetaFile.MAX_SUMMARY + " characters. None prints it")
    List<String> text;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var entry = PrpCommands.resolve(prpDir, index);
            if (text == null || text.isEmpty()) {
                var m = MetaFile.read(prpDir, entry.getIndex());
                PrpCommands.OUT.println(m == null || m.summary() == null ? "" : m.summary());
                return;
            }
            var m = MetaFile.update(prpDir, entry.getIndex(), TrackCli.uuid(entry),
                    x -> x.summary(String.join(" ", text).strip()));
            PrpCommands.OUT.println(entry.getIndex() + "  " + m.summary());
        });
    }
}
