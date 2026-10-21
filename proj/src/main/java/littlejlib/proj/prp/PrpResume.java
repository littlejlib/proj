package littlejlib.proj.prp;

import littlejlib.proj.Settings;
import littlejlib.proj.TerminalLauncher;
import littlejlib.proj.terminal.RunOutcome;
import littlejlib.proj.terminal.TerminalRunner;

import java.nio.file.Path;
import java.util.List;

/**
 * Getting back into a conversation this PRP already had.
 * <p>
 * The id comes from {@link PrpSessions} - proj passed it as {@code --session-id} when it started the
 * session, so nothing has to be recognised or searched for. For sessions proj did not start there is
 * {@code proj cct find}, which searches what was said in them.
 */
public final class PrpResume {

    private PrpResume() { }

    public record Plan(Path projectDir, PrpSession session, String command) { }

    /** The session to resume: the one named by id or by a prefix of it, else the most recent. */
    public static PrpSession pick(List<PrpSession> sessions, String wanted) {
        if (sessions.isEmpty()) return null;
        if (wanted == null || wanted.isBlank()) return sessions.get(0);
        return sessions.stream().filter(s -> s.sessionId() != null
                && s.sessionId().startsWith(wanted.trim())).findFirst().orElse(null);
    }

    public static Plan plan(Path prpFile, PrpSession session, Settings settings, boolean fork) {
        var project = session.project() != null ? session.project() : AgentLauncher.projectRootOf(prpFile);
        return new Plan(project, session, command(session, settings, fork));
    }

    /**
     * {@code claude --resume <id> --name "<tab>"}. Built from the agent template's program rather
     * than the word "claude", so a machine running a different agent still gets its own.
     */
    public static String command(PrpSession session, Settings settings, boolean fork) {
        var line = new StringBuilder(PrpPrompt.program(settings));
        line.append(" --resume ").append(session.sessionId());
        if (fork) line.append(" --fork-session");
        if (settings != null && settings.isAgentSkipPermissions())
            line.append(' ').append(PrpPrompt.SKIP_PERMISSIONS);
        if (session.tabName() != null && !session.tabName().isBlank())
            line.append(" --name \"").append(session.tabName()).append('"');
        return line.toString();
    }

    public static RunOutcome launch(Plan plan, Settings settings, TerminalLauncher terminal) {
        return launch(plan, settings, terminal, false);
    }

    /** @param newTerminal open a tab rather than resuming in the terminal this was typed in */
    public static RunOutcome launch(Plan plan, Settings settings, TerminalLauncher terminal,
                                    boolean newTerminal) {
        return TerminalRunner.outcomeOf(terminal, plan.projectDir(), plan.command(),
                "prp-resume", settings, plan.session().tabName(), newTerminal);
    }
}
