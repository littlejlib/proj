package littlejlib.proj.cli;

import littlejlib.proj.hib.HibStore;
import littlejlib.proj.hib.LiveSessions;
import littlejlib.proj.migrate.FolderMap;
import littlejlib.proj.migrate.SessionMigration;
import littlejlib.proj.migrate.TabsCtl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * After {@code proj prp renumber}: moves proj's session records, the hibernated conversations and the
 * open terminal tabs to the new PRP files, numbers and short names, from each folder's renumber-map.jsonl.
 */
@Command(name = "renumber-sessions", mixinStandardHelpOptions = true,
        description = "Follow renumbered PRP folders: update session records, hibernated conversations and open tab names. "
                + "A dry run unless --apply is given; nothing is ever closed")
public final class PrpRenumberSessionsCmd implements Runnable {

    @Option(names = {"-d", "--dir"}, description = "Where to look for prp folders holding a renumber-map.jsonl (default: the current folder)")
    String dir;

    @Option(names = "--depth", defaultValue = "4", description = "How many folder levels below --dir to search (default ${DEFAULT-VALUE})")
    int depth;

    @Option(names = "--apply", description = "Make the changes (default is a dry run)")
    boolean apply;

    @Override
    public void run() {
        var root = Path.of(dir == null ? "." : dir).toAbsolutePath().normalize();
        try {
            var folders = FolderMap.find(root, depth);
            if (folders.isEmpty()) {
                PrpCommands.OUT.println("no " + FolderMap.FILE + " within " + depth + " levels of " + root);
                return;
            }
            var tabs = TabsCtl.list();
            var run = new SessionMigration(apply, HibStore.all(), tabs, LiveSessions.all(), PrpCommands.OUT);
            for (var f : folders) run.folder(FolderMap.read(f));
            run.summary();
            PrpCommands.OUT.flush();
        } catch (java.io.IOException | RuntimeException e) {
            PrpCommands.ERR.println("renumber-sessions: " + e.getMessage());
            System.exit(2);
        }
    }
}
