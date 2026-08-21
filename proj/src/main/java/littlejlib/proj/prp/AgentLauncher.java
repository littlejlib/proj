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
    public static final String DEFAULT_AGENT_COMMAND = "claude %FLAGS% \"%PROMPT%\"";

    /**
     * What the default used to be, kept so that an existing settings file is not stuck with it.
     * <p>
     * A configured value always wins over the default - that is the point of configuring one. But
     * every settings file written before this change carries the OLD default verbatim, having never
     * been edited by anyone, and treating that as a deliberate choice would mean the new prompt
     * reached nobody who had ever run the tool. So this exact string reads as "not configured".
     */
    public static final String LEGACY_AGENT_COMMAND = "claude \"@%PRP% read this PRP and let us begin\"";

    /** The template actually in force: the configured one, unless it is blank or the old default. */
    public static String templateOf(Settings settings) {
        var configured = settings == null ? null : settings.getAgentCommand();
        return isBlank(configured) || LEGACY_AGENT_COMMAND.equals(configured.trim())
                ? DEFAULT_AGENT_COMMAND : configured;
    }

    public record Launch(Path projectDir, Path prpFile, String relativePrp, String command) { }

    /**
     * Works out what would be run, without running it. Separated so the caller can show the command
     * before starting a terminal, and so this is testable without launching anything.
     *
     * @param prpFile the PRP document; its project root is derived by stepping out of prp/
     */
    public static Launch plan(Path prpFile, Settings settings) {
        var project = projectRootOf(prpFile);
        return new Launch(project, prpFile, relativise(project, prpFile),
                PrpPrompt.command(prpFile, settings));
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
        var plan = plan(prpFile, settings);
        return TerminalRunner.runIn(terminal, plan.projectDir(), plan.command(),
                "prp-" + plan.prpFile().getFileName(), settings);
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
