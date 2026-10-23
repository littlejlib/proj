package littlejlib.proj.cli;

import littlejlib.proj.hib.Closer;
import littlejlib.proj.hib.Hibernator;
import littlejlib.proj.hib.LiveSessions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static littlejlib.proj.cli.PrpCommands.ERR;
import static littlejlib.proj.cli.PrpCommands.OUT;

/**
 * Hibernate claude-code conversations to free RAM without losing the task: export the log, record
 * it, close the tab; restore later with {@code claude --resume}. Typed as {@code !proj hib} inside
 * the conversation, so nothing costs tokens.
 */
@Command(name = "hib", aliases = "hibernate", mixinStandardHelpOptions = true,
        description = "Hibernate THIS claude-code conversation (export log, record, close tab). "
                + "Subcommands list, hibernate others and restore.",
        subcommands = {HibLsCmd.class, HibTabCmd.class, HibParkedCmd.class, HibRestoreCmd.class, HibUiCmd.class},
        synopsisSubcommandLabel = "[COMMAND]")
public final class HibCli implements Runnable {

    @Option(names = "--anyway", description = "Hibernate even when the conversation is not on a PRP")
    boolean anyway;

    @Option(names = "--dry-run", description = "Say what would happen, change nothing")
    boolean dryRun;

    @Override
    public void run() {
        var self = LiveSessions.self();
        if (self == null) {
            ERR.println("not inside a claude-code conversation. Type  !proj hib  in the conversation to "
                    + "hibernate it, or  proj hib ls  /  proj hib tab <name>  from anywhere.");
            System.exit(2);
            return;
        }
        System.exit(report(Hibernator.run(self, true, anyway, false, dryRun)));
    }

    static int report(Hibernator.Outcome o) {
        var out = o.exit() == 0 ? OUT : ERR;
        o.lines().forEach(out::println);
        out.flush();
        if (o.pendingClose() != null && !Closer.end(o.pendingClose())) {
            ERR.println("could not end it - close the tab by hand; the record is saved");
            return 3;
        }
        return o.exit();
    }
}
