package littlejlib.proj.cli;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpLogExport;
import littlejlib.proj.prp.PrpResume;
import littlejlib.proj.prp.PrpSession;
import littlejlib.proj.prp.PrpSessions;
import littlejlib.proj.cct.export.Stamp;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;

/**
 * Back into the conversation a PRP already had, in one step.
 * <p>
 * Only sessions proj itself started are here, because those are the ones whose id it minted. For
 * anything older, {@code proj cct find} searches what was said inside every session on the machine.
 */
@Command(name = "resume", description = "Resume the claude-code session recorded against a PRP")
public class PrpResumeCmd implements Runnable {

    @Parameters(arity = "0..1", description = "PRP index, e.g. 32. Defaults to the one this session opened on")
    String index;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Option(names = "--session", description = "Session id or a prefix of one. Defaults to the most recent")
    String session;

    @Option(names = "--list", description = "List what is recorded for this PRP and start nothing")
    boolean list;

    @Option(names = "--remember", arity = "0..1", fallbackValue = "",
            description = "Record a session against this PRP without starting anything. With no id, "
                    + "this directory's most recent claude-code session - which is how a session "
                    + "started by hand becomes resumable from here")
    String remember;

    @Option(names = "--tab", description = "The tab name to record with --remember")
    String tab;

    @Option(names = "--fork", description = "Resume into a NEW session, leaving the original untouched")
    boolean fork;

    @Option(names = "--print", description = "Print the command and exit, without opening a terminal")
    boolean dryRun;

    @Option(names = {"--new-terminal", "--new-tab"},
            description = "Open another terminal instead of resuming here. Default when there is "
                    + "no console to resume in, which is every launch from the picker")
    boolean newTerminal;

    @Override
    public void run() {
        var prpDir = PrpCommands.prpDir(dir);
        List<littlejlib.proj.prp.PrpEntry> all;
        try {
            all = PrpCreator.list(prpDir);
        } catch (java.io.IOException e) {
            PrpCommands.ERR.println("could not read " + prpDir + ": " + e.getMessage());
            System.exit(3);
            return;
        }
        var entry = PrpLogExport.pick(all, index, Path.of(System.getProperty("user.dir")));
        if (entry == null) {
            PrpCommands.ERR.println("no PRP " + (index == null ? "" : index + " ") + "under " + prpDir);
            System.exit(4);
            return;
        }

        if (remember != null) {
            remember(entry);
            return;
        }

        var sessions = PrpSessions.of(entry.getMainFile());
        if (sessions.isEmpty()) {
            PrpCommands.ERR.println("no session recorded for PRP " + entry.getIndex() + " - proj records "
                    + "one when it starts an agent, so a session started by hand is not here. "
                    + "Try:  proj cct find \"" + entry.getName() + "\"");
            System.exit(5);
            return;
        }

        if (list) {
            for (var s : sessions) PrpCommands.OUT.println(row(s));
            return;
        }

        var chosen = PrpResume.pick(sessions, session);
        if (chosen == null) {
            PrpCommands.ERR.println("no session of PRP " + entry.getIndex() + " starts with " + session);
            System.exit(5);
            return;
        }

        var settings = new SettingsManager().loadSettings();
        var plan = PrpResume.plan(entry.getMainFile(), chosen, settings, fork);
        if (dryRun) {
            PrpCommands.OUT.println("directory  " + plan.projectDir());
            PrpCommands.OUT.println("tab        " + chosen.tabName());
            PrpCommands.OUT.println("command    " + plan.command());
            return;
        }

        switch (PrpResume.launch(plan, settings, null, newTerminal)) {
            // The shell echoes the command as it runs it, so repeating it here is noise.
            case QUEUED -> PrpCommands.ERR.println("resuming in this terminal - --new-terminal "
                    + "opens a tab instead");
            case RAN -> PrpCommands.OUT.println("resumed in " + plan.projectDir()
                    + ":  " + plan.command());
            case OPENED_ONLY -> {
                PrpCommands.OUT.println(plan.command());
                PrpCommands.ERR.println(littlejlib.proj.terminal.RunOutcome.OPENED_ONLY.note());
            }
            case FAILED -> {
                PrpCommands.ERR.println("could not open a terminal - register one with 'proj settings'");
                System.exit(3);
            }
        }
    }

    /**
     * Attaching a conversation to a PRP after the fact. proj knows the id of every session it
     * started; one started by hand in a terminal is invisible to it until someone says so.
     */
    void remember(littlejlib.proj.prp.PrpEntry entry) {
        var prpFile = entry.getMainFile();
        var project = littlejlib.proj.prp.AgentLauncher.projectRootOf(prpFile);
        var id = remember.isBlank() ? latestSessionOf(project) : remember.trim();
        if (id == null) {
            PrpCommands.ERR.println("no claude-code session recorded for " + project);
            System.exit(5);
            return;
        }
        var name = tab != null && !tab.isBlank() ? tab.trim() : PrpSessions.tabName(prpFile);
        PrpSessions.record(id, prpFile, entry.getIndex(), project, name);
        PrpCommands.OUT.println("PRP " + entry.getIndex() + "  " + id + "  as  " + name);
    }

    /** The id is the transcript's file name, which is how claude-code stores it. */
    static String latestSessionOf(Path project) {
        try {
            var file = littlejlib.proj.prp.PrpLogExport.latestSession(project);
            var name = file.getFileName().toString();
            return name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String row(PrpSession s) {
        return s.sessionId() + "  " + Stamp.of(s.started()) + "  " + s.tabName();
    }
}
