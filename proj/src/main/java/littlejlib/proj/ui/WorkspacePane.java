package littlejlib.proj.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import littlejlib.proj.Settings;
import littlejlib.proj.group.ProjectGroup;
import littlejlib.proj.workspace.WorkspaceRecord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The folder list of a PLAIN group, with the PRP side panel beside it.
 */
public final class WorkspacePane {

    public final VBox root = new VBox();
    public final SplitPane split = new SplitPane();
    public final TextField filter = new TextField();
    public final TreeTableView<WorkspaceRecord> tree = new TreeTableView<>();
    public final PrpPanel prpPanel;
    public final WorkspaceScanBar scanBar;

    final ProjectGroup group;
    final Settings settings;
    final List<CheckBox> baseBoxes = new ArrayList<>();
    final Label count = new Label();
    final ChoiceBox<WorkspaceFilter> showBox = new ChoiceBox<>();
    final ChoiceBox<WorkspaceSort> sortBox = new ChoiceBox<>();
    final HBox legend = new HBox(4);

    List<WorkspaceRecord> rows = List.of();
    Runnable onLeaveUpward;
    java.util.function.Consumer<Path> onActivate;

    public WorkspacePane(ProjectGroup group, Settings settings) {
        this.group = group;
        this.settings = settings;
        this.prpPanel = new PrpPanel(settings, false, this::closePrp);
        // The arrow that points out of a pane leaves it, on THIS tab too. Without this the panel
        this.prpPanel.onExitLeft = this::focusTree;

        filter.setPromptText("filter " + group.displayLabel() + " ...");
        filter.textProperty().addListener((o, a, b) -> repopulate());
        filter.setOnKeyPressed(this::onFilterKey);

        WorkspaceColumns.install(tree);
        tree.setOnKeyPressed(this::onTreeKey);
        tree.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (split.getItems().size() > 1 && selectedDir() != null) prpPanel.showProject(selectedDir());
        });
        tree.setContextMenu(menu());

        split.getItems().add(tree);
        VBox.setVgrow(split, Priority.ALWAYS);

        showBox.getItems().setAll(WorkspaceFilter.values());
        showBox.setValue(WorkspaceFilter.ALL);
        showBox.setTooltip(new Tooltip(WorkspaceFilter.ALL.description));
        showBox.valueProperty().addListener((o, a, b) -> {
            if (b != null) showBox.setTooltip(new Tooltip(b.description));
            repopulate();
        });

        sortBox.getItems().setAll(WorkspaceSort.values());
        sortBox.setValue(WorkspaceSort.HOTTEST);
        sortBox.setTooltip(new Tooltip("How folders are ordered within their parent"));
        sortBox.valueProperty().addListener((o, a, b) -> repopulate());

        scanBar = new WorkspaceScanBar(group, settings, this::setRows);
        // A refresh started anywhere else in this process - the daemon's clock, or 'proj ws scan'
        // served by it - lands here. Skipped while this pane is doing its own, which sets the rows
        // itself and would otherwise pay for a second read of the whole group.
        littlejlib.proj.workspace.WorkspaceRefresh.onRefreshed(slug -> {
            if (!group.slug().equals(slug) || scanBar.busy()) return;
            javafx.application.Platform.runLater(() -> {
                setRows(GroupRefresh.rowsOf(group));
                scanBar.reloadStamp();
            });
        });

        var top = new HBox(6, new Label("Filter"), filter,
                new Label("Show"), showBox, new Label("Sort"), sortBox);
        HBox.setHgrow(filter, Priority.ALWAYS);
        // One tick box per configured folder and nothing more: primary/mirror/auxiliary is a code
        if (group.paths().size() > 1) {
            for (var p : group.paths()) {
                var box = new CheckBox(nameOf(p));
                box.setSelected(true);
                box.setTooltip(new Tooltip(p));
                box.getProperties().put("path", p);
                box.selectedProperty().addListener((o, a, b) -> repopulate());
                baseBoxes.add(box);
                top.getChildren().add(box);
            }
        }
        top.getChildren().add(scanBar.node);
        top.setPadding(new Insets(6, 6, 4, 6));

        count.setStyle("-fx-font-size: 9px; -fx-text-fill: #78909C;");
        buildLegend();
        var foot = new HBox(10, count, legend);
        foot.setPadding(new Insets(2, 8, 4, 8));

        root.getChildren().addAll(top, split, foot);
        Keys.noMnemonics(root);
    }

    /** Up out of the filter box leaves the pane; the owner decides where that is. */
    public void onLeaveUpward(Runnable handler) {
        this.onLeaveUpward = handler;
    }

    public void onActivate(java.util.function.Consumer<Path> handler) {
        this.onActivate = handler;
    }

    public void setRows(List<WorkspaceRecord> found) {
        this.rows = found == null ? List.of() : found;
        repopulate();
    }

    public WorkspaceRecord selected() {
        var item = tree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    public Path selectedDir() {
        var rec = selected();
        return rec == null ? null : rec.dir();
    }

    public void focusFilter() {
        filter.requestFocus();
    }

    /** Selects a folder by path and focuses it - what Alt+J from a transcript lands on. */
    public boolean reselect(String path) {
        if (path == null) return false;
        for (var i = 0; i < tree.getExpandedItemCount(); i++) {
            var item = tree.getTreeItem(i);
            if (item == null || item.getValue() == null) continue;
            if (path.equals(item.getValue().path())) {
                tree.getSelectionModel().select(i);
                tree.scrollTo(Math.max(0, i - 3));
                tree.requestFocus();
                return true;
            }
        }
        return false;
    }

    public void focusTree() {
        tree.requestFocus();
        if (tree.getSelectionModel().isEmpty() && tree.getExpandedItemCount() > 0) {
            tree.getSelectionModel().select(0);
        }
    }

    // ---------- the PRP side panel ----------

    /** Right arrow: open it if closed, then put the focus in it. Same as the projects table. */
    public void openPrp(boolean focus) {
        var dir = selectedDir();
        if (dir == null) return;
        if (split.getItems().size() == 1) {
            split.getItems().add(prpPanel.root);
            split.setDividerPositions(0.58);
        }
        prpPanel.showProject(dir);
        prpPanel.showFocus(focus);
        if (focus) javafx.application.Platform.runLater(prpPanel::focusTree);
    }

    public void closePrp() {
        split.getItems().remove(prpPanel.root);
        focusTree();
    }

    public boolean prpOpen() {
        return split.getItems().size() > 1;
    }

    // ---------- keys ----------

    void onFilterKey(KeyEvent e) {
        if (e.getCode() == KeyCode.DOWN) {
            focusTree();
            e.consume();
        } else if (e.getCode() == KeyCode.UP && onLeaveUpward != null) {
            onLeaveUpward.run();
            e.consume();
        }
    }

    void onTreeKey(KeyEvent e) {
        if (e.getCode() == KeyCode.UP && tree.getSelectionModel().getSelectedIndex() == 0) {
            focusFilter();
            e.consume();
        } else if (e.getCode() == KeyCode.RIGHT) {
            openPrp(true);
            e.consume();
        } else if (e.getCode() == KeyCode.ENTER) {
            activate();
            e.consume();
        }
    }

    /**
     * Every key this tab owns, from the scene so it works wherever the focus is.
     */
    public boolean onKey(KeyEvent e) {
        if (prpOpen() && prpPanel.holdsFocus()) {
            prpPanel.onKey(e);
            if (e.isConsumed()) return true;
        }
        if (prpPanel.holdsFocus()) return false;
        if (e.getCode() == null || !e.getCode().isFunctionKey()) return false;
        // F12 is Refresh on the projects tab and Refresh here, so it keeps its meaning across the
        if (e.getCode() == KeyCode.F12) {
            rescan();
            e.consume();
            return true;
        }
        var action = WorkspaceActions.byKey(e.getCode().getCode() - KeyCode.F1.getCode() + 1);
        if (action == null) return false;
        if (action == ProjectAction.PRP) openPrp(true);
        else if (action == ProjectAction.TERMINAL_CD) activate();
        else WorkspaceActions.perform(action, selectedDir(), settings);
        e.consume();
        return true;
    }

    void activate() {
        var dir = selectedDir();
        if (dir != null && onActivate != null) onActivate.accept(dir);
    }

    /**
     * An empty folder inside the selected one. Alt+N, the same key and the same helper as the
     * projects tab - a new case and a new project start life identically, as a directory with
     * nothing in it, and the thing that makes it one or the other is what goes in afterwards.
     */
    public void newFolder() {
        var base = group.existingBasePaths().isEmpty() ? null : group.existingBasePaths().get(0);
        var made = NewFolder.createUnder(NewFolder.parentFor(selectedDir(), base));
        if (made == null) return;
        // Shown at once rather than waiting for the next scan: a folder you cannot see is one you
        var rec = new WorkspaceRecord();
        var baseDir = base == null ? made.getParent() : base;
        rec.path(made.toAbsolutePath().toString())
                .base(baseDir.toAbsolutePath().toString())
                .groupSlug(group.slug())
                .name(made.getFileName().toString())
                .relativePath(baseDir.relativize(made).toString())
                .depth(baseDir.relativize(made).getNameCount())
                .parentPath(made.getParent().equals(baseDir) ? "" : made.getParent().toString())
                .lastActivity(System.currentTimeMillis());
        var grown = new ArrayList<>(rows);
        grown.add(rec);
        setRows(grown);
    }

    ContextMenu menu() {
        var m = new ContextMenu();
        var create = new MenuItem("Alt+N   New folder here");
        create.setOnAction(e -> newFolder());
        m.getItems().addAll(create, new SeparatorMenuItem());
        for (var action : WorkspaceActions.SUPPORTED) {
            var item = new MenuItem(action.keyName() + "   " + action.full);
            item.setOnAction(e -> {
                if (action == ProjectAction.PRP) openPrp(true);
                else if (action == ProjectAction.TERMINAL_CD) activate();
                else WorkspaceActions.perform(action, selectedDir(), settings);
            });
            m.getItems().add(item);
        }
        return m;
    }

    /** The legend is the toolbar: every entry wears its key and every entry is clickable. */
    void buildLegend() {
        legend.getChildren().clear();
        for (var action : WorkspaceActions.SUPPORTED) {
            var chip = Chip.of(action.keyName() + " " + action.label, action.style);
            chip.setCursor(javafx.scene.Cursor.HAND);
            chip.setTooltip(new Tooltip(action.tooltip));
            chip.setOnMouseClicked(e -> {
                if (action == ProjectAction.PRP) openPrp(true);
                else if (action == ProjectAction.TERMINAL_CD) activate();
                else WorkspaceActions.perform(action, selectedDir(), settings);
            });
            legend.getChildren().add(chip);
        }
        // A key belongs to a project whether or not that project builds. These rows are cases,
        // books and campaigns, and they get ticket numbers exactly like the code ones.
        var key = Chip.of("Alt+K project", Palette.GREY);
        key.setCursor(javafx.scene.Cursor.HAND);
        key.setTooltip(new Tooltip("Set this folder's short name, so its PRP 3 reads "
                + "<key>-03 and its terminal tab <key>-03-<task>"));
        key.setOnMouseClicked(e -> ProjectDialog.show(null, selectedDir(),
                () -> { if (prpOpen()) prpPanel.reload(); }));
        legend.getChildren().add(key);
        // F12 was bound here from the start and said nowhere, which on a tab whose bases are a
        // network drive is the one key you need and cannot guess.
        legend.getChildren().add(Legend.withTip(
                Legend.of("F12 refresh", Palette.GREY, this::rescan),
                "Re-walk " + group.displayLabel() + " now"));
    }

    // ---------- contents ----------

    /** A rescan of this group only - the counterpart of F12 on the projects tab. */
    public void rescan() {
        scanBar.rescan();
    }

    /** Re-walks only if the clock says it is due - what opening the tab does. */
    public void refreshIfDue() {
        scanBar.refreshIfDue();
    }

    void repopulate() {
        var text = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(java.util.Locale.ROOT);
        var included = includedBases();
        var show = showBox.getValue() == null ? WorkspaceFilter.ALL : showBox.getValue();
        var order = sortBox.getValue() == null ? WorkspaceSort.HOTTEST : sortBox.getValue();
        var now = System.currentTimeMillis();
        var visible = rows.stream()
                .filter(r -> included.isEmpty() || included.contains(r.base()))
                .filter(r -> show.test(r, now))
                .filter(r -> text.isEmpty() || match(r, text))
                .sorted(order.comparator())
                .toList();

        var root0 = new TreeItem<WorkspaceRecord>(null);
        WorkspaceTree.build(root0, visible, text.isEmpty());
        tree.setRoot(root0);
        var work = visible.stream().filter(WorkspaceRecord::isProject).count();
        var withPrps = visible.stream().filter(WorkspaceRecord::hasPrp).count();
        var repos = visible.stream().filter(WorkspaceRecord::isContainer).count();
        count.setText(visible.size() + " folders, " + work + " projects, " + repos + " repos, " + withPrps + " with PRPs"
                + (visible.size() == rows.size() ? "" : "   of " + rows.size() + " indexed"));
    }

    java.util.Set<String> includedBases() {
        if (baseBoxes.isEmpty()) return java.util.Set.of();
        var out = new java.util.HashSet<String>();
        for (var box : baseBoxes) {
            if (box.isSelected()) {
                out.add(Path.of((String) box.getProperties().get("path")).toAbsolutePath().toString());
            }
        }
        return out;
    }

    static boolean match(WorkspaceRecord r, String text) {
        return r.relativePath() != null
                && r.relativePath().toLowerCase(java.util.Locale.ROOT).contains(text);
    }

    static String nameOf(String path) {
        var p = Path.of(path).getFileName();
        return p == null ? path : p.toString();
    }
}
