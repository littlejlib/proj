package littlejlib.proj.cct.ui;

import module java.base;

/**
 * The few things the transcripts pane cannot decide for itself, because the answer belongs to
 * whoever is hosting it. Embedded in proj's picker these route into proj - its configured editor,
 * its configured terminal, and its project table, which is what makes "activate this project"
 * possible at all. Standalone they fall back to what a lone window can manage.
 */
public interface CctHost {

    void openInEditor(Path file);

    /** The terminal command template with {@code {dir}} in it. Null or blank means the default. */
    default String terminalCommand() { return null; }

    /**
     * Opens a terminal there. Overridden inside proj so this obeys the terminal the user actually
     * configured - somebody who installed Tabby did it to stop being handed a Command Prompt.
     */
    default boolean openTerminal(Path dir) {
        return dir != null && Shell.terminal(dir.toString(), terminalCommand());
    }

    /** Whether this host can put the picker on a project. False hides the action rather than failing it. */
    default boolean canActivateProject() { return false; }

    /** Show this directory as the selected project. Only called when {@link #canActivateProject()}. */
    default void activateProject(Path dir) { }

    static CctHost standalone() { return new StandaloneHost(); }
}
