package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import littlejlib.proj.Settings;
import littlejlib.proj.group.Groups;
import littlejlib.proj.group.ProjectGroup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The main window's tab strip: two tabs per GROUP - its rows and its PRPs - then Transcripts and
 * Settings. One flat level.
 */
public final class PickerTabs {

    public final TabPane pane = new TabPane();
    public final Tab projects, transcripts, settings;
    public final TranscriptsTab transcriptsTab;
    public final List<GroupTab> groups = new ArrayList<>();

    final Settings config;
    final Supplier<Stage> stage;
    final Supplier<List<Path>> knownProjects;
    final Runnable onProjects;

    public PickerTabs(Node projectsContent, Settings config, Supplier<Stage> stage, List<Path> roots,
                      Supplier<List<Path>> knownProjects, Runnable onProjects,
                      java.util.function.Consumer<Path> onActivateProject) {
        this.config = config;
        this.stage = stage;
        this.knownProjects = knownProjects;
        this.onProjects = onProjects;

        for (var group : Groups.of(config)) {
            groups.add(rowsTab(group, projectsContent));
            groups.add(prpsTab(group));
        }
        projects = groups.get(0).tab;

        transcriptsTab = new TranscriptsTab(config, onActivateProject);
        transcripts = new Tab("Transcripts");
        settings = new Tab("Settings");

        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        groups.forEach(g -> pane.getTabs().add(g.tab));
        pane.getTabs().addAll(transcripts, settings);
        retitle();
        pane.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> onSelected(b));
    }

    GroupTab rowsTab(ProjectGroup group, Node projectsContent) {
        if (group.isCode()) {
            return new GroupTab(group, false, new Tab(group.displayLabel(), projectsContent), null, null);
        }
        var folders = new WorkspacePane(group, config);
        return new GroupTab(group, false, new Tab(group.displayLabel(), folders.root), null, folders);
    }

    GroupTab prpsTab(ProjectGroup group) {
        var panel = new PrpPanel(config, true, null);
        return new GroupTab(group, true, new Tab(group.displayLabel() + " PRPs", panel.root), panel, null);
    }

    /** Each tab wears its key, because a key nobody can see is a key nobody has. */
    void retitle() {
        var all = pane.getTabs();
        for (var i = 0; i < all.size() && i < 9; i++) {
            var tab = all.get(i);
            tab.setTooltip(new Tooltip(tab.getText() + "   (Alt+" + (i + 1) + ")"));
        }
    }

    /**
     * True while Left/Right is walking the strip, so that passing OVER a tab does not pull the
     * focus down into it.
     */
    boolean walking;

    void onSelected(Tab selected) {
        var passingThrough = walking;
        if (selected == transcripts) {
            if (transcripts.getContent() == null) transcripts.setContent(transcriptsTab.build());
            transcriptsTab.start();
            if (!passingThrough) Platform.runLater(transcriptsTab::focusSearch);
            return;
        }
        if (selected == settings) {
            if (settings.getContent() == null) {
                settings.setContent(new SettingsPane(config, stage::get,
                        () -> Toast.show("settings saved - reopen the picker to pick up column changes"),
                        null).root);
            }
            return;
        }
        var group = groupOf(selected);
        if (group == null) return;
        GroupRefresh.onShown(group, knownProjects);
        if (passingThrough) return;
        if (group.isCodeRows() && onProjects != null) onProjects.run();
        else Platform.runLater(group::focusFilter);
    }

    public GroupTab groupOf(Tab tab) {
        return groups.stream().filter(g -> g.tab == tab).findFirst().orElse(null);
    }

    public GroupTab selectedGroup() {
        return groupOf(selected());
    }

    /** True only on the CODE group's rows tab - where the picker's own keys mean something. */
    public boolean codeProjectsShowing() {
        var g = selectedGroup();
        return g != null && g.isCodeRows();
    }

    public Tab selected() {
        return pane.getSelectionModel().getSelectedItem();
    }

    public void select(Tab tab) {
        pane.getSelectionModel().select(tab);
    }

    /** Alt and a digit, counting along the strip. */
    public boolean onAltDigit(KeyEvent e) {
        var all = pane.getTabs();
        for (var i = 0; i < all.size() && i < 9; i++) {
            if (Keys.alt(e, Keys.digit(i + 1))) {
                select(all.get(i));
                e.consume();
                return true;
            }
        }
        return false;
    }

    /**
     * Left and Right while the strip holds the focus, and Down to descend back into the pane - the
     * top of the same spatial rule the panes below already follow.
     */
    public boolean onStripArrow(KeyEvent e) {
        var all = pane.getTabs();
        var at = all.indexOf(selected());
        if (at < 0) return false;
        // Down is the only way the strip hands focus downward; Left/Right stay on it.
        if (e.getCode() == KeyCode.DOWN) {
            descend();
        } else if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
            var step = e.getCode() == KeyCode.LEFT ? -1 : 1;
            walking = true;
            try {
                select(all.get(Math.floorMod(at + step, all.size())));
            } finally {
                walking = false;
            }
            focusStrip();
        } else {
            return false;
        }
        e.consume();
        return true;
    }

    /**
     * Puts the focus back where typing belongs on whatever tab is showing - EVERY tab, including
     * the two that are not groups. Down off the strip has to land somewhere on all of them, or the
     * strip becomes a place you can get into and not out of.
     */
    public void descend() {
        var g = selectedGroup();
        if (g != null) {
            Platform.runLater(g::focusFilter);
        } else if (selected() == transcripts) {
            Platform.runLater(transcriptsTab::focusSearch);
        } else if (selected() == settings && settings.getContent() != null) {
            Platform.runLater(() -> settings.getContent().requestFocus());
        }
    }

    /**
     * Up out of a filter box lands here.
     */
    public void focusStrip() {
        pane.requestFocus();
    }

    public boolean stripHasFocus() {
        var scene = pane.getScene();
        if (scene == null) return false;
        var owner = scene.getFocusOwner();
        if (owner == pane) return true;
        for (var n = owner; n != null; n = n.getParent()) {
            if (n == pane) {
                // Inside the TabPane, but only the header counts - the content is a pane of its own.
                return n.getStyleClass().contains("tab-header-area") || owner.getStyleClass().contains("tab");
            }
            if (n.getStyleClass().contains("tab-header-area")) return true;
        }
        return false;
    }

    /** A tab by the name a command line used: {@code proj cct} asks for "transcripts". */
    public Tab byName(String name) {
        if (name == null || name.isBlank()) return projects;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "transcripts", "cct", "cctranscripts", "chats" -> transcripts;
            case "settings", "config" -> settings;
            case "prps", "prp", "global-prp", "recent" -> groups.get(1).tab;
            default -> bySlug(name);
        };
    }

    Tab bySlug(String name) {
        return groups.stream().filter(g -> !g.prps && name.equalsIgnoreCase(g.group.slug()))
                .map(g -> g.tab).findFirst().orElse(projects);
    }
}
