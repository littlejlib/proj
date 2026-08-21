package littlejlib.proj.ui;

import javafx.scene.Parent;
import javafx.scene.input.KeyEvent;
import littlejlib.proj.Settings;
import littlejlib.proj.cct.ui.CctHost;
import littlejlib.proj.cct.ui.MainWindow;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The transcripts pane, hosted as a tab.
 * <p>
 * It used to be a separate executable with a separate ArcadeDB directory, and that arrangement had
 * one unavoidable defect: two processes, two exclusive locks, so the window and the command line
 * could not both work. Here the index is a second database in the server the daemon already runs,
 * the picker holds it while it is open, and a {@code proj cct find} from a terminal is served by the
 * daemon exactly the way {@code proj list} always has been.
 * <p>
 * Built on FIRST SELECTION, like the other lazy tabs: opening the transcript index and running the
 * first search is not a cost to pay for a tab nobody has clicked.
 */
public final class TranscriptsTab implements CctHost {

    final Settings settings;
    final Consumer<Path> onActivateProject;
    MainWindow window;

    public TranscriptsTab(Settings settings, Consumer<Path> onActivateProject) {
        this.settings = settings;
        this.onActivateProject = onActivateProject;
    }

    public Parent build() {
        if (window == null) window = new MainWindow(this);
        return window.node();
    }

    public boolean built() { return window != null; }

    /** Opens the index and runs the first search. Safe to call again; the worker only opens once. */
    public void start() {
        if (window != null) window.start();
    }

    public void focusSearch() {
        if (window != null) window.queryField().requestFocus();
    }

    public void onKey(KeyEvent e) {
        if (window != null) window.actions().onKey(e);
    }

    /** Released when the picker hides, so nothing is held while nobody is looking at it. */
    public void release() {
        if (window != null) window.release();
    }

    public void dispose() {
        if (window != null) window.close();
    }

    @Override
    public void openInEditor(Path file) {
        PrpManagerActions.openInEditor(file, settings);
    }

    @Override
    public boolean openTerminal(Path dir) {
        return littlejlib.proj.terminal.TerminalRunner.open(dir, settings);
    }

    @Override
    public boolean canActivateProject() {
        return onActivateProject != null;
    }

    @Override
    public void activateProject(Path dir) {
        if (onActivateProject != null) onActivateProject.accept(dir);
    }
}
