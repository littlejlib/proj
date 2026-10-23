package littlejlib.proj.cli;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.hib.HibStore;
import littlejlib.proj.hib.Restorer;
import littlejlib.proj.terminal.RunOutcome;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static littlejlib.proj.cli.PrpCommands.ERR;
import static littlejlib.proj.cli.PrpCommands.OUT;

@Command(name = "restore", aliases = "resume", mixinStandardHelpOptions = true,
        description = "Reopen a hibernated conversation in a new tab, in its own folder, with claude --resume")
public final class HibRestoreCmd implements Runnable {

    @Parameters(description = "Name, name fragment or session id prefix (see 'proj hib parked')")
    String target;

    @Option(names = "--again", description = "Also match conversations already restored")
    boolean again;

    @Option(names = "--print", description = "Print the folder and command, open nothing")
    boolean print;

    @Override
    public void run() {
        var hits = HibStore.find(target, again);
        if (hits.size() != 1) {
            ERR.println(hits.isEmpty() ? "nothing hibernated matches " + target + (again ? "" : " (--again includes restored)")
                    : target + " matches " + hits.size() + ": " + hits.stream().map(r -> r.label()).toList());
            System.exit(4);
            return;
        }
        var r = hits.get(0);
        var settings = new SettingsManager().loadSettings();
        if (print) {
            OUT.println("directory  " + r.cwd());
            OUT.println("command    " + Restorer.command(r, settings));
            return;
        }
        if (!Restorer.transcriptExists(r)) {
            ERR.println("the transcript of " + r.label() + " is gone (claude-code prunes old ones), so it "
                    + "cannot be resumed. Its log is " + r.logFile());
            System.exit(5);
            return;
        }
        var outcome = Restorer.restore(r, settings);
        if (outcome == RunOutcome.FAILED) {
            ERR.println("could not open a terminal - register one with 'proj settings'");
            System.exit(3);
        }
        OUT.println("restored " + r.label() + " in " + r.cwd());
        if (outcome == RunOutcome.OPENED_ONLY) ERR.println(RunOutcome.OPENED_ONLY.note());
    }
}
