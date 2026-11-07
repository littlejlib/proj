package littlejlib.proj.prp;

import littlejlib.proj.Settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * What the agent is told when it is started on a PRP.
 * <p>
 * The old instruction was one line - {@code @prp/NN-name.md read this PRP and let us begin} - and it
 * was one line short of enough in two ways. It left the agent to discover the status file, which is
 * where every previous session recorded what was actually done and what was only claimed; and it
 * said nothing about the session logs, so a session five was told the specification and none of the
 * four conversations that had already argued about it.
 * <p>
 * So the prompt is now assembled from what is actually on disk:
 * <pre>
 * &#64;prp/27-prp-name.md &#64;prp/27-prp.status.md read the last 10 percent of the previous prp logs file
 * which is prp/27-prp.99.02.logs.txt
 * </pre>
 * EVERY CLAUSE IS CONDITIONAL ON ITS FILE EXISTING. A PRP written five minutes ago has no status
 * file and no logs, and naming either would send the agent looking for something that is not there -
 * which costs a tool call and, worse, teaches it that the paths in this prompt are guesses.
 */
public final class PrpPrompt {

    private PrpPrompt() { }

    /** No permission prompts. Off unless the user turns it on; see {@link Settings}. */
    public static final String SKIP_PERMISSIONS = "--dangerously-skip-permissions";

    /**
     * @param prpFile the PRP document; its project root is the parent of the prp folder
     * @return the instruction, with no surrounding quotes - the command template adds those
     */
    public static String of(Path prpFile, Settings settings) {
        return of(prpFile, settings, settings == null ? 10 : settings.getAgentLogsTailPercent());
    }

    /**
     * @param percent overrides the setting for THIS launch. The stored value is the habit; a
     *                particular session sometimes wants more of the last conversation than usual,
     *                and changing a setting to start one agent is a setting you then forget to
     *                change back.
     */
    public static String of(Path prpFile, Settings settings, int percent) {
        var project = AgentLauncher.projectRootOf(prpFile);
        var prpDir = prpFile.toAbsolutePath().normalize().getParent();
        var index = indexOf(prpFile);

        var parts = new ArrayList<String>();
        parts.add("@" + relative(project, prpFile));

        var status = prpDir == null || index == null ? null : prpDir.resolve(index + "-prp.status.md");
        if (status != null && Files.exists(status)) parts.add("@" + relative(project, status));

        var last = index == null ? null : PrpLogs.latest(prpDir, index);
        if (last != null && percent > 0) {
            parts.add("read the last " + percent + " percent of the previous prp logs file which is "
                    + relative(project, last.file()));
        }
        return String.join(" ", parts);
    }

    /** The whole command line, template and flags included, exactly as a shell will see it. */
    public static String command(Path prpFile, Settings settings) {
        return command(prpFile, settings, settings == null ? 10 : settings.getAgentLogsTailPercent());
    }

    public static String command(Path prpFile, Settings settings, int percent) {
        return command(prpFile, settings, percent, AgentLauncher.newSessionId(),
                TabName.of(prpFile, null));
    }

    /**
     * @param sessionId the id the conversation will have, so it can be recorded before it exists
     * @param tabName   what the tab is called; claude-code writes it as the terminal title and keeps
     *                  writing it, which is why no terminal has to be asked to rename anything
     */
    public static String command(Path prpFile, Settings settings, int percent,
                                 String sessionId, String tabName) {
        return command(prpFile, settings, percent, sessionId, tabName, null);
    }

    /** @param model what {@code %MODEL%} becomes: {@code --model <model>}, or nothing when null or blank */
    public static String command(Path prpFile, Settings settings, int percent,
                                 String sessionId, String tabName, String model) {
        return command(prpFile, settings, percent, sessionId, tabName, model, null);
    }

    /** @param exe the program to run instead of the template's own (fclaude, pclaude ...); null keeps it */
    public static String command(Path prpFile, Settings settings, int percent,
                                 String sessionId, String tabName, String model, String exe) {
        var project = AgentLauncher.projectRootOf(prpFile);

        // The TEMPLATE is expanded first and the PROMPT substituted into the result, never the
        // other way round. A PRP whose file name happened to contain %FLAGS% would otherwise have
        // it expanded, which is a placeholder injected by a file name - a small hole, and free to
        // close by ordering the two substitutions.
        return shell(settings, sessionId, tabName, model, exe)
                .replace("%PRP%", relative(project, prpFile))
                .replace("%PRP_ABS%", prpFile.toAbsolutePath().toString())
                .replace("%PATH%", project.toAbsolutePath().toString())
                .replace("%PROMPT%", of(prpFile, settings, percent));
    }

    /**
     * The template with everything expanded except the instruction itself. A missing tab name takes
     * its whole option with it rather than leaving {@code --name ""} behind, the same way
     * {@code %FLAGS%} takes the space in front of it.
     */
    static String shell(Settings settings, String sessionId, String tabName) {
        return shell(settings, sessionId, tabName, null);
    }

    static String shell(Settings settings, String sessionId, String tabName, String model) {
        return shell(settings, sessionId, tabName, model, null);
    }

    static String shell(Settings settings, String sessionId, String tabName, String model, String exe) {
        var filled = withModel(withFlags(AgentExe.withExe(AgentLauncher.templateOf(settings), exe), settings), model)
                .replace("%SESSION%", sessionId == null ? AgentLauncher.newSessionId() : sessionId);
        if (tabName != null && !tabName.isBlank()) return filled.replace("%NAME%", tabName.trim());
        return filled.replace(" --name \"%NAME%\"", "").replace(" --name %NAME%", "")
                .replace("%NAME%", "");
    }

    /** What the tab is called - see {@link TabName}, which is where the convention lives. */
    public static String tabName(Path prpFile) {
        return TabName.of(prpFile, null);
    }

    /**
     * Expands {@code %FLAGS%}, taking the space in front of it when there is nothing to put there.
     * <p>
     * Consuming that one space is the whole reason this is a method. Collapsing runs of whitespace
     * afterwards would have done the same job and would also have rewritten the instruction, which
     * by then contains file names that are none of this code's business.
     */
    static String withFlags(String template, Settings settings) {
        if (settings != null && settings.isAgentSkipPermissions()) {
            return template.replace("%FLAGS%", SKIP_PERMISSIONS);
        }
        return template.replace(" %FLAGS%", "").replace("%FLAGS%", "");
    }

    static final java.util.regex.Pattern MODEL_ID = java.util.regex.Pattern.compile("[A-Za-z0-9._-]+");

    /**
     * Expands {@code %MODEL%} to {@code --model <model>}, or to nothing with the space in front of
     * it. The value lands in a shell command line, so anything but a plain model name or id is refused.
     */
    static String withModel(String template, String model) {
        var flag = modelFlag(model);
        if (flag.isEmpty()) return template.replace(" %MODEL%", "").replace("%MODEL%", "");
        return template.replace("%MODEL%", flag);
    }

    /** The names offered where a person picks a model; any full model id is accepted as well. */
    public static final java.util.List<String> MODELS = java.util.List.of("haiku", "sonnet", "opus");

    /** {@code --model <m>}, or "" for null or blank. Refuses anything that is not a plain name or id. */
    public static String modelFlag(String model) {
        if (model == null || model.isBlank()) return "";
        var m = model.trim();
        if (!MODEL_ID.matcher(m).matches()) throw new IllegalArgumentException("not a model name: " + model);
        return "--model " + m;
    }

    /**
     * The command with the instruction left OFF - {@code claude --dangerously-skip-permissions} and
     * nothing more.
     * <p>
     * For the third way of starting: the prompt goes to the clipboard and the agent comes up empty,
     * so it can be pasted and EDITED before it is sent. Half the time a PRP needs one extra sentence
     * for this particular session, and a command that has already submitted its prompt is a command
     * you have to interrupt to add it.
     * <p>
     * Built by REMOVING the prompt slot rather than by cutting the template at its first quote,
     * which stopped working the moment a quoted flag came before it.
     */
    public static String commandWithoutPrompt(Settings settings) {
        return commandWithoutPrompt(settings, null, null);
    }

    public static String commandWithoutPrompt(Settings settings, String sessionId, String tabName) {
        return commandWithoutPrompt(settings, sessionId, tabName, null);
    }

    public static String commandWithoutPrompt(Settings settings, String sessionId, String tabName,
                                              String model) {
        return commandWithoutPrompt(settings, sessionId, tabName, model, null);
    }

    public static String commandWithoutPrompt(Settings settings, String sessionId, String tabName,
                                              String model, String exe) {
        return shell(settings, sessionId, tabName, model, exe)
                .replace("\"%PROMPT%\"", "").replace("%PROMPT%", "")
                .replaceAll("\\s+", " ").trim();
    }

    /** The program the template runs, so a resume line does not have to assume it is claude. */
    public static String program(Settings settings) {
        return program(settings, null);
    }

    /** @param exe an explicit program that wins over the template's own; null or blank keeps it */
    public static String program(Settings settings, String exe) {
        var e = AgentExe.check(exe);
        if (e != null) return e;
        var argv = littlejlib.proj.util.Cmd.parse(AgentLauncher.templateOf(settings));
        return argv.length == 0 ? "claude" : argv[0];
    }

    /** The PRP's two-digit index, taken from its file name. Null for a file that is not one. */
    public static String indexOf(Path prpFile) {
        var name = prpFile.getFileName() == null ? "" : prpFile.getFileName().toString();
        var m = java.util.regex.Pattern.compile("^(\\d{2})-prp[-.]").matcher(name);
        return m.find() ? m.group(1) : null;
    }

    /** Forward slashes, because it goes into a quoted argument that a shell will see. */
    public static String relative(Path project, Path file) {
        try {
            return project.toAbsolutePath().normalize()
                    .relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.toAbsolutePath().toString().replace('\\', '/');
        }
    }
}
