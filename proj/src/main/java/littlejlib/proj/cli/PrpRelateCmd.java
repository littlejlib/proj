package littlejlib.proj.cli;

import littlejlib.proj.track.MetaFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * How a PRP relates to another, stored by the other PRP's uuid in NN-prp.meta.json so it survives
 * renames and moves. The inverse ("superseded-by" seen from 48) is derived by the index, not written twice.
 */
@Command(name = "relate", description = "Record how a PRP relates to another (NN-prp.meta.json)")
public class PrpRelateCmd implements Runnable {

    @Parameters(index = "0", description = "PRP index")
    String index;

    @Parameters(index = "1", description = "parent, child, supersedes, superseded-by, depends-on, blocks, reports-to, sibling, related")
    String kind;

    @Parameters(index = "2", description = "The other PRP: a number in this project, or its whole uuid")
    String target;

    @Option(names = "--note", description = "A few words on the relation")
    String note;

    @Option(names = "--remove", description = "Remove the relation to that PRP instead")
    boolean remove;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var entry = PrpCommands.resolve(prpDir, index);
            var self = TrackCli.uuid(entry);
            var other = TrackCli.target(prpDir, target);
            if (other.equals(self)) throw new IllegalArgumentException("a PRP cannot relate to itself");
            var k = kind.strip().toLowerCase();
            MetaFile.update(prpDir, entry.getIndex(), self, m -> MetaFile.relate(m, other, remove ? null : k, note));
            PrpCommands.OUT.println(entry.getIndex() + (remove ? "  no longer related to " : "  " + k + " ") + other);
        });
    }
}
