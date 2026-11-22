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
        return plan(prpFile, session, settings, fork, null);
    }

    /** @param model haiku, sonnet, opus or a model id to resume ON; null keeps the user's default */
    public static Plan plan(Path prpFile, PrpSession session, Settings settings, boolean fork,
                            String model) {
        return plan(prpFile, session, settings, fork, model, null);
    }

    /** @param exe the program to resume ON (fclaude ...); null or blank means the template's own */
    public static Plan plan(Path prpFile, PrpSession session, Settings settings, boolean fork,
                            String model, String exe) {
        var project = session.project() != null ? session.project() : AgentLauncher.projectRootOf(prpFile);
        return new Plan(project, session, command(session, settings, fork, model, exe));
    }

    /**
     * Which program to resume on. An explicit {@code --exe} is taken as given. Otherwise a session that
     * ran on something other than the default is a question - resuming it on plain claude would move a
     * free-lane conversation onto the paid account without anyone choosing that - so {@code confirm} is
     * asked with the recorded name, and anything but a yes means the default.
     *
     * @return the explicit program, or null for the default
     */
    public static String exeFor(PrpSession session, String explicit,
                                java.util.function.Predicate<String> confirm) {
        var given = AgentExe.check(explicit);
        if (given != null) return given;
        var recorded = session == null ? null : session.exe();
        if (AgentExe.isDefault(recorded)) return null;
        return confirm.test(recorded.trim()) ? recorded.trim() : null;
    }

    /**
     * {@code claude --resume <id> --name "<tab>"}. Built from the agent template's program rather
     * than the word "claude", so a machine running a different agent still gets its own.
     */
    public static String command(PrpSession session, Settings settings, boolean fork) {
        return command(session, settings, fork, null);
    }

    public static String command(PrpSession session, Settings settings, boolean fork, String model) {
        return command(session, settings, fork, model, null);
    }

    public static String command(PrpSession session, Settings settings, boolean fork, String model,
                                 String exe) {
        var line = new StringBuilder(PrpPrompt.program(settings, exe));
        line.append(" --resume ").append(session.sessionId());
        var flag = PrpPrompt.modelFlag(model);
        if (!flag.isEmpty()) line.append(' ').append(flag);
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
