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
 * &#64;prp/27-prp-name.md &#64;prp/27-prp.status.md read 10% of the end of the previous prp logs file
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
            parts.add("read " + percent + "% of the end of the previous prp logs file which is "
                    + relative(project, last.file()));
        }
        return String.join(" ", parts);
    }

    /** The whole command line, template and flags included, exactly as a shell will see it. */
    public static String command(Path prpFile, Settings settings) {
        return command(prpFile, settings, settings == null ? 10 : settings.getAgentLogsTailPercent());
    }

    public static String command(Path prpFile, Settings settings, int percent) {
        var project = AgentLauncher.projectRootOf(prpFile);

        // The TEMPLATE is expanded first and the PROMPT substituted into the result, never the
        // other way round. A PRP whose file name happened to contain %FLAGS% would otherwise have
        // it expanded, which is a placeholder injected by a file name - a small hole, and free to
        // close by ordering the two substitutions.
        return withFlags(AgentLauncher.templateOf(settings), settings)
                .replace("%PRP%", relative(project, prpFile))
                .replace("%PRP_ABS%", prpFile.toAbsolutePath().toString())
                .replace("%PATH%", project.toAbsolutePath().toString())
                .replace("%PROMPT%", of(prpFile, settings, percent));
    }

    /**
     * What the terminal tab is called: the PRP's name, made readable.
     * {@code 27-prp-prp_side_panel_and_sorting_order.md} becomes
     * {@code PRP side panel and sorting order}.
     * <p>
     * Taken from the tabs already open on this machine, which read exactly that way. No index in it,
     * for the same reason those do not have one - a tab is identified by what it is about, and the
     * number is only useful next to the other numbers.
     * <p>
     * It is a STARTING title, and worth knowing why: claude-code rewrites the terminal title as it
     * works, prefixing its own progress glyph, so this is what the tab says until the agent takes
     * over and after it finishes. Tabby's MCP has no rename-tab verb, so a name that survives the
     * agent is not something this can offer.
     */
    public static String tabTitle(Path prpFile) {
        var name = prpFile.getFileName() == null ? "" : prpFile.getFileName().toString();
        var m = java.util.regex.Pattern.compile("^\\d{2}-prp-(.+?)(\\.closed)?\\.md$").matcher(name);
        var words = (m.matches() ? m.group(1) : name.replaceAll("\\.md$", ""))
                .replace('_', ' ').replace('-', ' ').trim();
        if (words.isEmpty()) return "PRP";
        // "prp side panel..." reads as an acronym at the front of a title, so it is written as one.
        if (words.regionMatches(true, 0, "prp ", 0, 4)) return "PRP " + words.substring(4);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
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

    /**
     * The command with the instruction left OFF - {@code claude --dangerously-skip-permissions} and
     * nothing more.
     * <p>
     * For the third way of starting: the prompt goes to the clipboard and the agent comes up empty,
     * so it can be pasted and EDITED before it is sent. Half the time a PRP needs one extra sentence
     * for this particular session, and a command that has already submitted its prompt is a command
     * you have to interrupt to add it.
     * <p>
     * Derived by cutting the template at its first quote rather than hardcoding "claude", so a user
     * who runs a different agent still gets their own program here.
     */
    public static String commandWithoutPrompt(Settings settings) {
        var filled = withFlags(AgentLauncher.templateOf(settings), settings);
        var quote = filled.indexOf('"');
        return (quote < 0 ? filled.replace("%PROMPT%", "") : filled.substring(0, quote)).trim();
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
