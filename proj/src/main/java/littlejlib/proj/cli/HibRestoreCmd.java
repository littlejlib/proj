package littlejlib.proj.cli;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.hib.HibStore;
import littlejlib.proj.hib.Restorer;
import littlejlib.proj.terminal.RunOutcome;
import java.nio.file.Path;
import littlejlib.proj.prp.AgentExe;
import littlejlib.proj.prp.PrpResume;
import littlejlib.proj.prp.PrpSessions;
import littlejlib.proj.hib.HibRecord;
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

    @Option(names = "--model", paramLabel = "<model>",
            description = "Model to resume on: haiku, sonnet, opus or a full model id. Without it claude takes its default")
    String model;

    @Option(names = "--exe", paramLabel = "<name>",
            description = "Which claude to restore on, by bare name (fclaude, pclaude ...), found on PATH. Without it, "
                    + "a PRP session recorded on another claude asks first; no answer in 10 seconds means plain claude")
    String exe;

    @Option(names = "--print", description = "Print the folder and command, open nothing")
    boolean print;

    @Override
    public void run() {
        try {
            exe = AgentExe.require(exe);
        } catch (IllegalArgumentException e) {
            ERR.println(e.getMessage());
            System.exit(2);
            return;
        }
        var hits = HibStore.find(target, again);
        if (hits.size() != 1) {
            ERR.println(hits.isEmpty() ? "nothing hibernated matches " + target + (again ? "" : " (--again includes restored)")
                    : target + " matches " + hits.size() + ": " + hits.stream().map(r -> r.label()).toList());
            System.exit(4);
            return;
        }
        var r = hits.get(0);
        var settings = new SettingsManager().loadSettings();
        var program = exe;
        try {
            if (program == null && !print) program = recordedExe(r);
            if (program != null) AgentExe.require(program);
            Restorer.command(r, settings, model, program);
        } catch (IllegalArgumentException e) {
            ERR.println(e.getMessage());
            System.exit(2);
            return;
        }
        if (print) {
            OUT.println("directory  " + r.cwd());
            OUT.println("command    " + Restorer.command(r, settings, model, program));
            return;
        }
        if (!Restorer.transcriptExists(r)) {
            ERR.println("the transcript of " + r.label() + " is gone (claude-code prunes old ones), so it "
                    + "cannot be resumed. Its log is " + r.logFile());
            System.exit(5);
            return;
        }
        var outcome = Restorer.restore(r, settings, model, program);
        if (outcome == RunOutcome.FAILED) {
            ERR.println("could not open a terminal - register one with 'proj settings'");
            System.exit(3);
        }
        OUT.println("restored " + r.label() + " in " + r.cwd());
        if (outcome == RunOutcome.OPENED_ONLY) ERR.println(RunOutcome.OPENED_ONLY.note());
    }

    /** The program the PRP session of this conversation ran on, when it was not plain claude and the user says to keep it. */
    static String recordedExe(HibRecord r) {
        if (r.prpFile() == null || r.prpFile().isBlank()) return null;
        var session = PrpSessions.of(Path.of(r.prpFile())).stream()
                .filter(s -> r.sessionId() != null && r.sessionId().equals(s.sessionId())).findFirst().orElse(null);
        return PrpResume.exeFor(session, null, ResumeChoice::askExe);
    }
}
