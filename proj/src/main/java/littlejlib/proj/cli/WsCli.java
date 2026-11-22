package littlejlib.proj.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The non-code groups, as one verb group: {@code proj ws list}, {@code proj ws scan}.
 */
@Command(
        name = "ws",
        mixinStandardHelpOptions = true,
        description = "Folders that carry PRPs, in the groups configured as 'plain'.",
        subcommands = {WsListCmd.class, WsScanCmd.class},
        synopsisSubcommandLabel = "COMMAND")
public final class WsCli implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.err);
    }
}
