package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpDirFinder;
import littlejlib.proj.prp.PrpUuid;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

/**
 * The uuid of a PRP, which lives in the PRP file. With an index it prints that PRP's uuid (writing it
 * first if the file has none); with {@code --backfill} it gives every PRP without one a uuid.
 */
@Command(name = "uuid", description = "Show a PRP's uuid, or give every PRP that has none a uuid (--backfill)")
public class PrpUuidCmd implements Runnable {

    @Parameters(arity = "0..1", description = "PRP index. Defaults to the PRP this session opened on")
    String index;

    @Option(names = {"-d", "--dir"}, description = "Project directory, its prp folder, or with --backfill any folder above projects")
    String dir;

    @Option(names = "--backfill", description = "Write a uuid into every PRP file under the folder that has none")
    boolean backfill;

    @Option(names = "--depth", description = "With --backfill: how many folder levels below the folder to look for prp folders (default 0)")
    int depth;

    @Option(names = "--dry-run", description = "With --backfill: say what would change, and change nothing")
    boolean dryRun;

    @Override
    public void run() {
        if (backfill) backfill();
        else show();
    }

    void show() {
        var entry = PrpCommands.resolve(PrpCommands.prpDir(dir), index);
        var uuid = PrpUuid.read(entry.getMainFile());
        if (uuid == null) {
            PrpCommands.ERR.println("could not give " + entry.getMainFile().getFileName() + " a uuid");
            System.exit(3);
            return;
        }
        PrpCommands.OUT.println(uuid);
    }

    void backfill() {
        var root = dir == null || dir.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(dir);
        try {
            var seen = new HashMap<String, Path>();
            var added = 0;
            var had = 0;
            for (var prpDir : PrpDirFinder.find(root, depth)) {
                for (var entry : PrpCreator.list(prpDir)) {
                    var file = entry.getMainFile();
                    var uuid = PrpUuid.read(file);
                    if (uuid == null) {
                        added++;
                        PrpCommands.OUT.println((dryRun ? "would add  " : "added  ") + file);
                        if (!dryRun) uuid = PrpUuid.ensure(file, true).uuid();
                    } else had++;
                    var other = uuid == null ? null : seen.put(uuid, file);
                    if (other != null) PrpCommands.ERR.println("DUPLICATE " + uuid + "\n  " + other + "\n  " + file);
                }
            }
            PrpCommands.OUT.println((dryRun ? "would add " : "added ") + added + ", already had " + had);
        } catch (IOException e) {
            PrpCommands.ERR.println("could not read " + root + ": " + e.getMessage());
            System.exit(3);
        }
    }
}
