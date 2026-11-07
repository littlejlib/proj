package littlejlib.proj.prp;

import littlejlib.proj.Settings;
import littlejlib.proj.terminal.TerminalRunner;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts a coding agent on a PRP, in the right directory, in one step.
 * <p>
 * The manual version of this is: make the PRP, copy its relative path, open a terminal somewhere,
 * navigate to the project, paste. Three of those five steps are places to get it wrong, and the
 * commonest mistake is the one this class exists to make impossible - starting the agent inside
 * {@code prp/} rather than at the project root. An agent started one directory too deep sees a
 * folder of markdown and none of the code, and every path it is given afterwards is wrong.
 * <p>
 * So the working directory is ALWAYS the project root, and the PRP is referred to by a path relative
 * to that root ({@code prp/14-prp-name.md}), which is both what the agent needs in order to open it
 * and what a human reading the scrollback can recognise.
 */
public final class AgentLauncher {

    private AgentLauncher() { }

    /**
     * The default command. The instruction itself is built by {@link PrpPrompt} from what is on
     * disk - the PRP, its status file, and the tail of the last session's log - so the template's
     * whole job is to say which program runs it and where the flags go.
     * <p>
     * {@code %FLAGS%} resolves to {@code --dangerously-skip-permissions} or to nothing, from one
     * setting. It is a placeholder rather than baked into the string so that turning it off does not
     * require editing a command line by hand and getting the quoting right.
     */
    public static final String DEFAULT_AGENT_COMMAND =
            "claude %FLAGS% %MODEL% --session-id %SESSION% --name \"%NAME%\" \"%PROMPT%\"";

    /**
     * What the default used to be, kept so that an existing settings file is not stuck with it.
     * <p>
     * A configured value always wins over the default - that is the point of configuring one. But
     * every settings file written before this change carries the OLD default verbatim, having never
     * been edited by anyone, and treating that as a deliberate choice would mean the new prompt
     * reached nobody who had ever run the tool. So these exact strings read as "not configured".
     */
    public static final java.util.Set<String> LEGACY_AGENT_COMMANDS = java.util.Set.of(
            "claude \"@%PRP% read this PRP and let us begin\"",
            "claude %FLAGS% \"%PROMPT%\"",
            "claude %FLAGS% --session-id %SESSION% --name \"%NAME%\" \"%PROMPT%\"");

    /** The template actually in force: the configured one, unless it is blank or an old default. */
    public static String templateOf(Settings settings) {
        var configured = settings == null ? null : settings.getAgentCommand();
        return isBlank(configured) || LEGACY_AGENT_COMMANDS.contains(configured.trim())
                ? DEFAULT_AGENT_COMMAND : configured;
    }

    /**
     * The id the session will have. Minted here rather than discovered afterwards: claude-code takes
     * {@code --session-id}, so the PRP-to-conversation link can be exact from the first moment
     * instead of being reconstructed later from what the first prompt happened to say.
     */
    public static String newSessionId() {
        return java.util.UUID.randomUUID().toString();
    }

    /** @param exe the explicit program the command runs, or null when it is the template's own */
    public record Launch(Path projectDir, Path prpFile, String relativePrp, String command,
                         String sessionId, String tabName, String exe) { }

    /**
     * Works out what would be run, without running it. Separated so the caller can show the command
     * before starting a terminal, and so this is testable without launching anything.
     *
     * @param prpFile the PRP document; its project root is derived by stepping out of prp/
     */
    public static Launch plan(Path prpFile, Settings settings) {
        return plan(prpFile, settings, newSessionId(), PrpSessions.tabName(prpFile));
    }

    public static Launch plan(Path prpFile, Settings settings, String model) {
        return plan(prpFile, settings, newSessionId(), PrpSessions.tabName(prpFile), model);
    }

    public static Launch plan(Path prpFile, Settings settings, String sessionId, String tabName) {
        return plan(prpFile, settings, sessionId, tabName, null);
    }

    /** @param model haiku, sonnet, opus or a full model id; null or blank leaves it to the user's settings */
    public static Launch plan(Path prpFile, Settings settings, String sessionId, String tabName,
                              String model) {
        return plan(prpFile, settings, sessionId, tabName, model, null);
    }

    /** A new session id and the remembered tab name, with a model and an explicit program. */
    public static Launch planWith(Path prpFile, Settings settings, String model, String exe) {
        return plan(prpFile, settings, newSessionId(), PrpSessions.tabName(prpFile), model, exe);
    }

    /** @param exe the program to run instead of the template's (fclaude ...); null or blank keeps it */
    public static Launch plan(Path prpFile, Settings settings, String sessionId, String tabName,
                              String model, String exe) {
        var project = projectRootOf(prpFile);
        var percent = settings == null ? 10 : settings.getAgentLogsTailPercent();
        var program = AgentExe.check(exe);
        return new Launch(project, prpFile, relativise(project, prpFile),
                PrpPrompt.command(prpFile, settings, percent, sessionId, tabName, model, program),
                sessionId, tabName, program);
    }

    public static boolean launch(Path prpFile, Settings settings) {
        return launch(prpFile, settings, null);
    }

    /**
     * @param terminal which terminal to use, or null for whichever one settings resolve to. Named
     *                 explicitly because the launch dialog offers them all and lets you pick per
     *                 launch - the configured default is the answer for scripts, not for a person
     *                 who can see three terminals installed and knows which one this job wants.
     */
    public static boolean launch(Path prpFile, Settings settings, littlejlib.proj.TerminalLauncher terminal) {
        return launch(plan(prpFile, settings), settings, terminal);
    }

    /** Launching a plan ALREADY MADE, so the session id that was recorded is the one that runs. */
    public static boolean launch(Launch plan, Settings settings, littlejlib.proj.TerminalLauncher terminal) {
        return start(plan, settings, terminal, false).opened();
    }

    /**
     * The same, saying WHERE it went. {@code QUEUED} means it will run in the caller's own terminal
     * once the caller exits, which is a thing the caller has to know rather than guess.
     *
     * @param newTerminal open a tab rather than using the terminal this was typed in
     */
    public static littlejlib.proj.terminal.RunOutcome start(
            Launch plan, Settings settings, littlejlib.proj.TerminalLauncher terminal,
            boolean newTerminal) {
        ProjFile.prepare(plan.projectDir());
        return TerminalRunner.outcomeOf(terminal, plan.projectDir(), plan.command(),
                "prp-" + plan.prpFile().getFileName(), settings, plan.tabName(), newTerminal);
    }

    /**
     * The project root: the parent of the {@code prp} folder the document sits in.
     * <p>
     * Falls back to the file's own directory when it is not under a prp folder at all, which is the
     * honest answer for a PRP kept somewhere unconventional - better than walking up to a directory
     * that has nothing to do with it.
     */
    public static Path projectRootOf(Path prpFile) {
        var dir = prpFile.toAbsolutePath().normalize().getParent();
        if (dir == null) return Path.of(System.getProperty("user.dir"));

        var name = dir.getFileName();
        if (name != null && name.toString().equalsIgnoreCase("prp") && dir.getParent() != null) {
            return dir.getParent();
        }
        return dir;
    }

    /** Forward slashes, because it goes into a quoted argument that a shell will see. */
    static String relativise(Path project, Path prpFile) {
        try {
            return project.toAbsolutePath().normalize()
                    .relativize(prpFile.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (Exception e) {
            return prpFile.toAbsolutePath().toString().replace('\\', '/');
        }
    }

    /** The PRP of a project by index, for the command line. Null when there is no such PRP. */
    public static Path findPrp(Path projectDir, String index) throws java.io.IOException {
        var prpDir = PrpCreator.prpDirOf(projectDir);
        if (!Files.isDirectory(prpDir)) return null;
        var wanted = String.format("%02d", Integer.parseInt(index.trim()));
        return PrpCreator.list(prpDir).stream()
                .filter(p -> wanted.equals(p.getIndex()))
                .map(littlejlib.proj.prp.PrpEntry::getMainFile)
                .findFirst().orElse(null);
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
