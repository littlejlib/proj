package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import littlejlib.proj.Settings;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * The main window's top level: Projects, Recent PRPs, Settings.
 * <p>
 * Settings used to be a modal window opened from a menu opened from this window - three gestures to
 * reach a path you can already see is wrong. The global PRP list was another modal, reached the same
 * way, and it is the thing you want FIRST on a morning when you cannot remember which of nine
 * projects you left half-finished. Both are now a tab.
 * <p>
 * CONTENT IS BUILT ON FIRST SELECTION. This window is constructed at daemon startup so that the
 * first {@code proj o} is instant; the settings pane walks the disk for terminals and IDEs, and the
 * global PRP list walks every code base. Neither is a cost anyone should pay for a tab they have not
 * opened, and the global scan is a cost worth paying only when it is being looked at.
 * <p>
 * SWITCHED WITH ALT AND A DIGIT. Every bare key belongs to the filter box and every function key is
 * already an action, so a modifier is unavoidable - and switching tabs is the rare thing genuinely
 * done once a session, which is what Alt is reserved for. See guidelines.keyboard-shortcuts.
 */
public final class PickerTabs {

    public final TabPane pane = new TabPane();
    public final Tab projects, global, transcripts, settings;
    public final PrpPanel globalPanel;
    public final TranscriptsTab transcriptsTab;

    final Settings config;
    final Supplier<Stage> stage;
    final List<Path> roots;
    final Supplier<List<Path>> knownProjects;
    final Runnable onProjects;

    /**
     * @param knownProjects where the projects are, from the index the picker has already loaded.
     *                     Supplied rather than walked: see {@code GlobalPrpScanner.scanProjects}
     */
    public PickerTabs(Node projectsContent, Settings config, Supplier<Stage> stage, List<Path> roots,
                      Supplier<List<Path>> knownProjects, Runnable onProjects,
                      java.util.function.Consumer<Path> onActivateProject) {
        this.config = config;
        this.stage = stage;
        this.roots = roots;
        this.knownProjects = knownProjects;
        this.onProjects = onProjects;

        projects = new Tab("Projects", projectsContent);
        projects.setTooltip(new Tooltip("The project index   (Alt+1)"));

        globalPanel = new PrpPanel(config, true, null);
        global = new Tab("Recent PRPs", globalPanel.root);
        global.setTooltip(new Tooltip("The " + PrpPanel.GLOBAL_LIMIT
                + " most recently active PRPs, across every project   (Alt+2)"));

        transcriptsTab = new TranscriptsTab(config, onActivateProject);
        transcripts = new Tab("Transcripts");
        transcripts.setTooltip(new Tooltip("Every claude-code conversation on this machine, "
                + "searched by what was said in it   (Alt+3)"));

        settings = new Tab("Settings");
        settings.setTooltip(new Tooltip("Code bases, terminals, editors, the agent   (Alt+4)"));

        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pane.getTabs().addAll(projects, global, transcripts, settings);
        pane.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> onSelected(b));
    }

    void onSelected(Tab selected) {
        if (selected == transcripts) {
            if (transcripts.getContent() == null) transcripts.setContent(transcriptsTab.build());
            transcriptsTab.start();
            Platform.runLater(transcriptsTab::focusSearch);
        }
        if (selected == settings && settings.getContent() == null) {
            settings.setContent(new SettingsPane(config, stage::get,
                    () -> Toast.show("settings saved - reopen the picker to pick up column changes"),
                    null).root);
        }
        if (selected == global) {
            // Scanned once per open of the tab rather than once ever: PRPs are edited constantly,
            // including by the agents this window starts, so a list cached from an hour ago would
            // be wrong in exactly the way that matters.
            globalPanel.showGlobal(this::findPrps);
            globalPanel.showFocus(true);
            Platform.runLater(globalPanel::focusTree);
        } else {
            globalPanel.showFocus(false);
        }
        if (selected == projects && onProjects != null) onProjects.run();
    }

    /**
     * Runs off the UI thread. Uses the loaded project index when there is one - which is nearly
     * always, because this tab cannot be clicked before the window that loads it is open - and falls
     * back to walking the code bases when there is not, so an empty index shows PRPs rather than
     * nothing at all.
     */
    List<littlejlib.proj.prp.GlobalPrpEntry> findPrps() {
        var known = knownProjects == null ? List.<Path>of() : knownProjects.get();
        if (!known.isEmpty()) return littlejlib.proj.prp.GlobalPrpScanner.scanProjects(known);

        var all = new java.util.ArrayList<littlejlib.proj.prp.GlobalPrpEntry>();
        for (var root : roots) {
            all.addAll(littlejlib.proj.prp.GlobalPrpScanner
                    .scanAllProjects(root, config.getProjectScanningDepth()));
        }
        return all;
    }

    public Tab selected() {
        return pane.getSelectionModel().getSelectedItem();
    }

    public void select(Tab tab) {
        pane.getSelectionModel().select(tab);
    }

    /** Alt+1/2/3/4. Returns true when the key was one of them, so the caller can stop. */
    public boolean onAltDigit(javafx.scene.input.KeyEvent e) {
        if (Keys.alt(e, javafx.scene.input.KeyCode.DIGIT1)) select(projects);
        else if (Keys.alt(e, javafx.scene.input.KeyCode.DIGIT2)) select(global);
        else if (Keys.alt(e, javafx.scene.input.KeyCode.DIGIT3)) select(transcripts);
        else if (Keys.alt(e, javafx.scene.input.KeyCode.DIGIT4)) select(settings);
        else return false;
        e.consume();
        return true;
    }

    /** A tab by the name a command line used: {@code proj cct} asks for "transcripts". */
    public Tab byName(String name) {
        if (name == null || name.isBlank()) return projects;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "transcripts", "cct", "cctranscripts", "chats" -> transcripts;
            case "prps", "prp", "global-prp", "recent" -> global;
            case "settings", "config" -> settings;
            default -> projects;
        };
    }
}
