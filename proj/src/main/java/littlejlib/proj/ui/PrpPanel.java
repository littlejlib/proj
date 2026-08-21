package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import littlejlib.proj.Settings;
import littlejlib.proj.prp.GlobalPrpEntry;
import littlejlib.proj.prp.PrpCreator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A list of PRPs, as a NODE rather than a window.
 * <p>
 * The same component is the side panel beside the project list, the body of the popup PRP manager,
 * and the global tab. It has to be one component: the three of them were three hand-written copies
 * of the same tree, the same search box and the same switch statement over the same actions, and
 * they had already drifted - different key sets, different button labels, and a {@code @prp/} path
 * that was right in one and wrong in another.
 * <p>
 * KEYS. Function keys, per {@link PrpAction}, handled through {@link #onKey} rather than by a filter
 * of this panel's own - because in the picker this panel shares a Scene with a projects table that
 * binds the same F-keys, and only the host knows which of the two has the focus. In the popup, where
 * there is no competition, the host is a two-line stage that forwards everything.
 */
public final class PrpPanel {

    final Settings settings;
    final boolean global;
    final Runnable onClose;
    /** Where Left goes: back to the pane on the left. Null when there is nothing to the left. */
    Runnable onExitLeft;

    public final VBox root = new VBox(6);
    final Label title = new Label(), status = new Label();
    javafx.scene.layout.FlowPane legendPane;
    final TextField search = new TextField();
    final ChoiceBox<PrpSort> sortBox = new ChoiceBox<>();
    final Button newButton = new Button("F1  New PRP"), refreshButton = new Button("F12  Refresh");
    final TreeView<PrpNode> tree = new TreeView<>();

    /** How many of the most recently active PRPs the global list keeps. See {@link #showGlobal}. */
    public static final int GLOBAL_LIMIT = 50;

    List<GlobalPrpEntry> entries = List.of();
    Path projectDir;
    java.util.concurrent.Callable<List<GlobalPrpEntry>> globalSource;
    int globalTotal;
    // Bumped per load so a slow global scan cannot land on top of a newer one's results.
    int loadGeneration;

    public PrpPanel(Settings settings, boolean global, Runnable onClose) {
        this.settings = settings;
        this.global = global;
        this.onClose = onClose;
        build();
    }

    void build() {
        title.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #1565C0;");
        var headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);

        var header = new HBox(8, title, headSpacer);
        header.setAlignment(Pos.CENTER_LEFT);
        if (onClose != null) {
            // Esc closes it too, and says so. A panel with no visible way out reads as a thing that
            // has taken the window over rather than a thing you opened.
            var close = new Button("×");
            close.setTooltip(new Tooltip("Close this panel   (Esc)"));
            close.setFocusTraversable(false);
            close.setStyle("-fx-font-size: 14; -fx-padding: 0 7 2 7;" + Palette.GREY);
            close.setOnAction(e -> onClose.run());
            header.getChildren().add(close);
        }

        search.setPromptText("Filter by index or name");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.textProperty().addListener((o, a, b) -> rebuild());

        sortBox.getItems().setAll(PrpSort.values());
        // The global list is a "what have I been doing" list, so it opens in that order. Within one
        // project the PRP number IS the order things happened in, so newest-index is the same answer
        // and reads better - across projects it is just a number that means nothing on its own.
        sortBox.setValue(global ? PrpSort.RECENT : PrpSort.NEWEST);
        sortBox.setTooltip(new Tooltip("The order this list is read in"));
        sortBox.valueProperty().addListener((o, a, b) -> rebuild());

        newButton.setTooltip(new Tooltip("Create the next numbered PRP from the template   (F1)"));
        newButton.setStyle("-fx-font-size: 11; -fx-padding: 3 9 3 9; -fx-background-radius: 3;" + Palette.GREEN);
        newButton.setFocusTraversable(false);
        newButton.setOnAction(e -> createNew());
        newButton.setVisible(!global);
        newButton.setManaged(!global);

        refreshButton.setTooltip(new Tooltip("Rescan   (F12)"));
        refreshButton.setStyle("-fx-font-size: 11; -fx-padding: 3 9 3 9; -fx-background-radius: 3;" + Palette.BLUE);
        refreshButton.setFocusTraversable(false);
        refreshButton.setOnAction(e -> reload());

        var tools = new HBox(6, search, sortBox, newButton, refreshButton);
        tools.setAlignment(Pos.CENTER_LEFT);

        tree.setShowRoot(false);
        tree.setCellFactory(t -> new PrpPanelCell(this::perform, global));
        VBox.setVgrow(tree, Priority.ALWAYS);

        status.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

        legendPane = legend();
        root.setPadding(new Insets(8));
        root.getChildren().addAll(header, tools, tree, status, legendPane);
        root.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-width: 0 0 0 1;");
        Keys.noMnemonics(root);
    }

    /**
     * The panel's own legend, rather than the window's. The projects table beside it binds the same
     * function keys to different actions, so the only honest place to print these is next to the
     * list they act on, where they can be read together with the header that lights up when this
     * panel is the one holding the focus.
     */
    javafx.scene.layout.FlowPane legend() {
        var pane = new javafx.scene.layout.FlowPane(4, 3);
        pane.getChildren().add(Chip.of("↑↓ move, ↑ at top returns to filter", Palette.GREY));
        // onClose rather than onExitLeft: the legend is built inside the constructor and the host
        // assigns onExitLeft after it returns. The two are set together in every case that has
        // either - the side panel has both, the popup and the global tab have neither.
        if (onClose != null) pane.getChildren().add(Chip.of("← back to projects", Palette.GREY));
        if (!global) pane.getChildren().add(Chip.of("F1 new", Palette.GREY));
        for (var a : PrpAction.values()) {
            pane.getChildren().add(Chip.of(a.legendText(), a.style, a.icon, a.iconColor));
        }
        pane.getChildren().add(Chip.of("F12 refresh", Palette.GREY));
        if (onClose != null) pane.getChildren().add(Chip.of("Esc close panel", Palette.GREY));
        return pane;
    }

    // ---------- data ----------

    /** Point the panel at a project. Reads the prp folder off the UI thread. */
    public void showProject(Path projectDir) {
        this.projectDir = projectDir;
        var label = projectDir.getFileName() == null ? projectDir.toString()
                : projectDir.getFileName().toString();
        title.setText("PRPs - " + label);
        status.setText("reading " + PrpCreator.prpDirOf(projectDir) + " ...");
        load(projectDir);
    }

    public void reload() {
        if (global) loadGlobal();
        else if (projectDir != null) load(projectDir);
    }

    /**
     * Every PRP the supplier can find, newest activity first, capped at {@link #GLOBAL_LIMIT}.
     * <p>
     * The cap is not a performance measure - everything has already been read by the time it
     * applies. It is what makes the list answer its question. "What have I been working on" has an
     * answer about fifty items long; the full set here is several hundred, and a list that long is
     * a second thing to search rather than a thing to read. The status line says what was left out.
     *
     * @param source runs OFF the UI thread; the panel decides when, and ignores a slow one that
     *               lands after a newer request
     */
    public void showGlobal(java.util.concurrent.Callable<List<GlobalPrpEntry>> source) {
        globalSource = source;
        title.setText("Recent PRPs - every project");
        loadGlobal();
    }

    void loadGlobal() {
        if (globalSource == null) return;
        status.setText("looking for PRPs across every project ...");
        var generation = ++loadGeneration;
        var source = globalSource;
        new Thread(() -> {
            List<GlobalPrpEntry> found;
            try {
                found = new ArrayList<>(source.call());
            } catch (Exception e) {
                System.err.println("[PrpPanel] global scan failed: " + e);
                found = new ArrayList<>();
            }
            found.sort(PrpSort.RECENT.comparator());
            var capped = List.copyOf(found.subList(0, Math.min(GLOBAL_LIMIT, found.size())));
            var total = found.size();
            Platform.runLater(() -> {
                if (generation != loadGeneration) return;
                globalTotal = total;
                setEntries(capped);
            });
        }, "PrpPanel-GlobalScan").start();
    }

    /**
     * The project a prp folder belongs to. Callers that already hold a project directory should
     * pass it; this exists for the two entry points that were written around a prp path - the
     * daemon menu and the old dialog API - and for a PRP kept somewhere unconventional, where the
     * honest answer is the folder itself rather than a parent that has nothing to do with it.
     */
    public static Path projectOf(Path prpDir) {
        var dir = prpDir.toAbsolutePath().normalize();
        var name = dir.getFileName();
        return name != null && name.toString().equalsIgnoreCase("prp") && dir.getParent() != null
                ? dir.getParent() : dir;
    }

    void load(Path dir) {
        new Thread(() -> {
            List<GlobalPrpEntry> found;
            try {
                found = GlobalPrpEntry.forProject(dir);
            } catch (Exception e) {
                found = List.of();
            }
            var result = found;
            Platform.runLater(() -> {
                // A slower read of a project you have since arrowed away from must not overwrite
                // the list you are now looking at.
                if (!dir.equals(projectDir)) return;
                entries = result;
                rebuild();
            });
        }, "PrpPanel-Load").start();
    }

    public void setEntries(List<GlobalPrpEntry> found) {
        entries = found == null ? List.of() : found;
        rebuild();
    }

    void rebuild() {
        var sort = sortBox.getValue() == null ? PrpSort.NEWEST : sortBox.getValue();
        var kept = new ArrayList<>(entries.stream().filter(matcher()).toList());
        kept.sort(sort.comparator());

        var rootItem = new TreeItem<PrpNode>();
        for (var e : kept) rootItem.getChildren().add(itemFor(e));
        tree.setRoot(rootItem);
        if (!rootItem.getChildren().isEmpty()) tree.getSelectionModel().select(0);

        // TreeView has no placeholder, so the empty case is said in the status line - and it is
        // worth saying, because "no PRPs" and "this project has no prp folder at all" look
        // identical on screen and only one of them is answered by F1.
        if (entries.isEmpty()) {
            status.setText(global ? "no PRPs found under any code base"
                    : "no PRPs in this project yet   -   F1 creates the first one");
        } else if (global) {
            // Says what was left out. A capped list that does not admit to being capped reads as
            // the whole set, and then "it is not in the list" gets taken for "it does not exist".
            status.setText(kept.size() + " shown, the " + entries.size() + " most recently active of "
                    + globalTotal + " PRPs across every project");
        } else {
            status.setText(kept.size() + " of " + entries.size() + " PRPs"
                    + "   -   function keys act here while this panel has the focus");
        }
    }

    TreeItem<PrpNode> itemFor(GlobalPrpEntry e) {
        var item = new TreeItem<>(PrpNode.of(e));
        item.getChildren().add(new TreeItem<>(PrpNode.file(e, e.getMainFile())));
        item.getChildren().add(new TreeItem<>(e.getPrpEntry().hasStatusFile()
                ? PrpNode.file(e, e.getPrpEntry().getStatusFile()) : PrpNode.missingStatus(e)));
        for (var sub : e.getPrpEntry().getSubFiles()) {
            item.getChildren().add(new TreeItem<>(PrpNode.file(e, sub)));
        }
        return item;
    }

    java.util.function.Predicate<GlobalPrpEntry> matcher() {
        var text = search.getText();
        if (text == null || text.isBlank()) return e -> true;
        var regex = Pattern.compile(String.join(".*", text.trim().split("\\s+")), Pattern.CASE_INSENSITIVE);
        return e -> regex.matcher(e.getIndex() + " " + e.getName() + " " + e.getProjectName()).find();
    }

    // ---------- actions and keys ----------

    void perform(PrpAction action, PrpNode node) {
        PrpActions.perform(action, node, settings, stage(), status::setText);
        // The rename lands on disk before this returns, so a reload here shows the new state rather
        // than leaving a row that says ACTIVE over a file now called .closed.md.
        if (PrpActions.mutatesList(action)) reload();
    }

    void createNew() {
        if (global || projectDir == null) return;
        PrpManagerActions.createNewPrp(settings, PrpCreator.prpDirOf(projectDir));
        reload();
    }

    /** Everything this panel answers to. The host decides WHEN to call it; see {@link PrpPanel}. */
    public void onKey(KeyEvent e) {
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;
        var code = e.getCode();

        if (code == KeyCode.ESCAPE && onClose != null) { onClose.run(); e.consume(); return; }
        if (code == KeyCode.F1 && !global) { createNew(); e.consume(); return; }
        if (code == KeyCode.F12) { reload(); e.consume(); return; }

        // THE ARROW THAT POINTS OUT OF THIS PANE LEAVES IT. This panel is the right-hand one, so
        // Left goes back to the projects; Right is left alone and still expands a tree node, which
        // is the mirror image of the rule on the other side. See ProjectPickerDialog.onArrowKey.
        if (code == KeyCode.LEFT && onExitLeft != null) { onExitLeft.run(); e.consume(); return; }

        if (code == KeyCode.DOWN && search.isFocused()) { focusTree(); e.consume(); return; }
        // Up off the top of the list returns to the filter, the same way it does in the projects
        // table - so the two panes are worked identically and the filter is always one Up away.
        if (code == KeyCode.UP && tree.isFocused()
                && tree.getSelectionModel().getSelectedIndex() <= 0) {
            focusSearch();
            e.consume();
            return;
        }
        // Enter is "open the thing", the same as F2 - because that is what Enter means in every
        // list of files anyone has ever used, and F2 is what the legend can afford to print.
        if (code == KeyCode.ENTER) {
            var node = selected();
            if (node != null) perform(PrpAction.OPEN, node);
            e.consume();
            return;
        }
        if (tree.isFocused() && code == KeyCode.SPACE) {
            var item = tree.getSelectionModel().getSelectedItem();
            if (item != null) item.setExpanded(!item.isExpanded());
            e.consume();
            return;
        }

        var action = PrpAction.byKey(code);
        if (action != null) {
            var node = selected();
            if (node == null) status.setText("select a PRP first");
            else if (!action.supportedBy(node)) status.setText(action.full + " does not apply to that row");
            else perform(action, node);
            e.consume();
            return;
        }

        // Typing anywhere in the panel is typing into its filter, the same rule the projects table
        // follows - otherwise the tree's own prefix type-ahead eats the letters.
        if (tree.isFocused()) typeIntoSearch(e);
    }

    void typeIntoSearch(KeyEvent e) {
        if (e.getCode() == KeyCode.BACK_SPACE) {
            var text = search.getText();
            search.requestFocus();
            if (text != null && !text.isEmpty()) search.setText(text.substring(0, text.length() - 1));
            search.positionCaret(search.getText().length());
            e.consume();
            return;
        }
        var typed = e.getText();
        if (typed == null || typed.isEmpty() || typed.codePointAt(0) < 0x20) return;
        search.requestFocus();
        search.appendText(typed);
        search.positionCaret(search.getText().length());
        e.consume();
    }

    public PrpNode selected() {
        var item = tree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    /** Where the focus lands on entering this panel: the filter, exactly as in the projects pane. */
    public void focusSearch() {
        search.requestFocus();
        search.positionCaret(search.getText() == null ? 0 : search.getText().length());
    }

    public void focusTree() {
        tree.requestFocus();
        if (tree.getSelectionModel().isEmpty() && tree.getRoot() != null
                && !tree.getRoot().getChildren().isEmpty()) {
            tree.getSelectionModel().select(0);
        }
    }

    /** True while the focus is anywhere inside this panel, which is what gives it the F-keys. */
    public boolean holdsFocus() {
        var scene = root.getScene();
        if (scene == null) return false;
        for (var n = scene.getFocusOwner(); n != null; n = n.getParent()) {
            if (n == root) return true;
        }
        return false;
    }

    /**
     * Says which pane the keys are going to, without the user having to work it out.
     * <p>
     * Two legends are on screen at once and they disagree about F2 - correctly, because they
     * describe two panes. So the one that is not currently live is FADED rather than removed:
     * removing it would make the window jump every time the focus moved, and leaving both at full
     * strength is what would actually mislead.
     */
    public void showFocus(boolean focused) {
        title.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: "
                + (focused ? "#1565C0" : "#9E9E9E") + ";");
        root.setStyle("-fx-background-color: #FAFAFA; -fx-border-width: 0 0 0 3; -fx-border-color: "
                + (focused ? "#1565C0" : "#E0E0E0") + ";");
        legendPane.setOpacity(focused ? 1.0 : 0.4);
    }

    Stage stage() {
        var scene = root.getScene();
        return scene == null || !(scene.getWindow() instanceof Stage s) ? null : s;
    }
}
