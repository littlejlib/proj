package littlejlib.proj.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import littlejlib.proj.CodeRepoBaseType;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.Settings;
import littlejlib.proj.central.CentralChecker;
import littlejlib.proj.model.DependencyIndex;
import littlejlib.proj.model.ProjectConcerns;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.scanner.ProjectStore;
import littlejlib.proj.scanner.ProjectTreeScanner;
import littlejlib.proj.scanner.ProjectWatcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The merged picker: mm's tree of maven facts and proj's keyboard-first selection, over one flat
 * row set. "Maven only" turns it into mm. Browses every configured code directory, which is mm's
 * multi-codedir support.
 * <p>
 * THE KEYBOARD MODEL, which everything else here bends to accommodate. Four families of key, each
 * owning a job nothing else touches, so no key ever means two things and no context has to be
 * switched to reach one:
 * <ul>
 *   <li>letters and digits type into the filter, from wherever the focus is - press one while the
 *       table has focus and it lands in the filter box, with the focus, mid-word;</li>
 *   <li>arrows, page and home/end move the selection, from wherever the focus is;</li>
 *   <li>function keys run the actions, from wherever the focus is;</li>
 *   <li>Alt and a letter works the view toggles, which are the only things here a bare key cannot
 *       be spent on because every bare key is already spoken for.</li>
 * </ul>
 * The old scheme gave the actions single letters, which meant they were unreachable while typing a
 * filter - the state this dialog spends nearly all its life in - and a digit shortcut for jumping to
 * a row, which collided with every project whose name contains a number. Both are gone.
 * <p>
 * The toggles were the last survivors of that scheme, as JavaFX mnemonics: {@code "_Auxiliary"} took
 * the bare A and typing the name of a project starting with one ticked the box instead of filtering.
 * They are now Alt-only and handled here rather than by JavaFX - see {@link Keys} for why a mnemonic
 * cannot be trusted to keep asking for Alt.
 * <p>
 * THE WINDOW HAS TABS, and the projects are one of them. Settings used to be a modal opened from a
 * menu opened from this window, which is a long way to travel to fix a path you can see is wrong.
 * Alt and a digit switches; the contents of a tab other than this one are built the first time it is
 * selected, because this window is constructed at daemon startup and nobody should pay for a tab
 * they never open.
 * <p>
 * THE PRP LIST SITS BESIDE THE TABLE, in a {@link PrpPanel} inside a SplitPane, opened by a single
 * click on a row or by F10 and closed by its own × or by Esc. It follows the selection once open,
 * so arrowing down the projects walks their PRPs alongside. The popup PRP manager still exists and
 * is still reachable - {@code proj} run inside a project goes straight to it, with no project list
 * to sit beside.
 * <p>
 * That panel binds the SAME function keys as this table, to different actions, because both sets
 * predate it. The focus decides which pane a key reaches; the focused pane wears a coloured header
 * and its legend is the one drawn at full strength, the other fading to show it is not listening.
 */
public class ProjectPickerDialog {

    public enum Action { NONE, EXECUTED }

    /**
     * Fixed, and load-bearing. {@code WindowsForeground} finds this window by its exact title in
     * order to take the foreground for it after a global-key summon, and a title that varied with
     * the browse path would have made that work on some machines and not others.
     */
    public static final String TITLE = "proj - project picker";

    final Settings settings;
    final List<Path> browsePaths;
    final ProjectStore store = new ProjectStore();
    final CentralChecker central = new CentralChecker();
    ProjectWatcher watcher;

    List<ProjectRecord> records = new ArrayList<>();
    DependencyIndex index = new DependencyIndex(List.of(), List.of());
    Map<String, ProjectConcerns> concerns = new HashMap<>();
    PickerResult result;

    Stage stage;
    TextField searchField;
    CheckBox mavenOnly, showFolders, includeMirror, includeAuxiliary;
    ChoiceBox<ConcernFilter> showBox;
    ChoiceBox<ProjectSort> sortBox;
    Button addBaseButton, refreshButton, prpButton, menuButton, newFolderButton;
    TreeTableView<ProjectRecord> table;
    ContextMenu rowMenu;
    PickerTabs tabs;
    SplitPane split;
    PrpPanel prpPanel;
    PauseTransition prpFollowDebounce;
    javafx.scene.layout.FlowPane legendPane;
    // Where the tab chips go. They cannot be built with the rest: the legend is inside the node that
    // is PASSED TO PickerTabs, so the tabs do not exist yet when this pane is assembled.
    int tabChipAt;
    // Resolved once. Detection walks the disk, and the row menu is opened often enough that doing
    // it per open would be felt. A settings change already asks for the picker to be reopened.
    List<littlejlib.proj.ide.DetectedIde> ides = List.of();
    Label counts, toast, progressLabel;
    ProgressBar progressBar;
    PauseTransition repaintDebounce;
    boolean syncingSort;

    ProjectPickerDialog(List<Path> browsePaths, Settings settings) {
        this.browsePaths = browsePaths;
        this.settings = settings;
        build();
    }

    void build() {
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(TITLE);
        AppIcon.on(stage);

        // Fit the screen actually in front of the user. A fixed 1400x760 overflowed a 1536x864
        // display and took the maven-only checkbox and the whole shortcut line off-screen with it.
        // These are the RESTORED bounds - what you get after un-maximising - because the window
        // opens maximised: this table is a dozen columns wide and every one of them is worth
        // reading. Restore is deliberately left available rather than the window being locked.
        var screen = Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1500, screen.getWidth() - 60));
        stage.setHeight(Math.min(820, screen.getHeight() - 60));
        stage.setX(screen.getMinX() + (screen.getWidth() - stage.getWidth()) / 2);
        stage.setY(screen.getMinY() + (screen.getHeight() - stage.getHeight()) / 2);
        // Set once, at build. The daemon shows this same window many times, and forcing it on
        // every reopen would undo an un-maximise the user had made deliberately.
        stage.setMaximized(true);

        searchField = new TextField();
        searchField.setPromptText("Filter - space separated terms, matches path and maven coordinates");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((o, a, b) -> repopulate());
        searchField.setOnKeyPressed(this::onSearchKey);

        showBox = new ChoiceBox<>();
        showBox.getItems().setAll(ConcernFilter.values());
        showBox.setValue(ConcernFilter.ALL);
        showBox.setTooltip(new Tooltip("Show only projects with a particular concern"));
        showBox.valueProperty().addListener((o, a, b) -> {
            if (b != null) showBox.setTooltip(new Tooltip(b.description));
            // Asking for the plain folders is asking for them, so the box that hides them gets out
            // of the way rather than answering the question with an empty table.
            if (b == ConcernFilter.FOLDER) showFolders.setSelected(true);
            repopulate();
        });

        sortBox = new ChoiceBox<>();
        sortBox.getItems().setAll(ProjectSort.values());
        sortBox.setValue(ProjectSort.RELEVANCE);
        sortBox.valueProperty().addListener((o, a, b) -> {
            // Choosing here means "stop sorting by that column", otherwise the two controls would
            // both claim to decide the order and the loser would silently do nothing.
            if (!syncingSort) table.getSortOrder().clear();
            repopulate();
        });

        mavenOnly = new CheckBox("Maven only");
        mavenOnly.setTooltip(new Tooltip("Show only maven projects - this is the old mm view   (Alt+M)"));
        mavenOnly.selectedProperty().addListener((o, a, b) -> repopulate());

        // On by default: a directory that holds no pom is still a place you work, and leaving them
        // out is exactly what made organisation directories and pom-less projects unreachable here.
        showFolders = new CheckBox("Folders");
        showFolders.setSelected(true);
        showFolders.setTooltip(new Tooltip(
                "Also list plain directories - no build file, nothing under git   (Alt+F)"));
        showFolders.selectedProperty().addListener((o, a, b) -> repopulate());

        includeMirror = new CheckBox("GitHub mirror");
        includeMirror.setTooltip(new Tooltip(CodeRepoBaseType.GITHUB_MIRROR.description + "   (Alt+G)"));
        includeMirror.selectedProperty().addListener((o, a, b) -> repopulate());

        includeAuxiliary = new CheckBox("Auxiliary");
        includeAuxiliary.setTooltip(new Tooltip(CodeRepoBaseType.AUXILIARY.description + "   (Alt+A)"));
        includeAuxiliary.selectedProperty().addListener((o, a, b) -> repopulate());

        addBaseButton = new Button("+ Base");
        addBaseButton.setTooltip(new Tooltip(
                "Add a coderepobase - a directory holding many repositories   (Alt+B)"));
        addBaseButton.setOnAction(e -> addCodeRepoBase());

        newFolderButton = new Button("+ Folder");
        newFolderButton.setTooltip(new Tooltip(
                "Create an empty folder inside the selected one - the start of a new project   (Alt+N)"));
        newFolderButton.setOnAction(e -> newFolder());

        prpButton = new Button("PRP");
        prpButton.setTooltip(new Tooltip("PRPs of the selected project   (F10)"));
        prpButton.setOnAction(e -> openPrpManager());

        menuButton = new Button("Menu");
        menuButton.setTooltip(new Tooltip("New PRP, global PRP viewer, coderepobases   (F11)"));
        menuButton.setOnAction(e -> openMenu());

        refreshButton = new Button("Refresh");
        refreshButton.setTooltip(new Tooltip("Rescan every coderepobase   (F12)"));
        refreshButton.setOnAction(e -> rescan());

        var row1 = new HBox(8, new Label("Filter"), searchField,
                new Label("Show"), showBox, new Label("Sort"), sortBox);
        var row2 = new HBox(8, mavenOnly, showFolders, includeMirror, includeAuxiliary,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                addBaseButton, newFolderButton, prpButton, menuButton, refreshButton);
        row1.setAlignment(Pos.CENTER_LEFT);
        row2.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(6);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressLabel = new Label();
        progressLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        table = new TreeTableView<>();
        table.setShowRoot(false);
        table.setTableMenuButtonVisible(true);
        ProjectPickerColumns.install(table, settings, central, this::concernsOf,
                this::scheduleRepaint, this::onAction, this::onLaunch);
        table.setOnKeyPressed(this::onTableKey);
        table.setOnMouseClicked(this::onTableClick);
        ides = ProjectPickerColumns.detectIdes(settings);
        installRowMenu();
        table.getSortOrder().addListener((javafx.collections.ListChangeListener<TreeTableColumn<ProjectRecord, ?>>)
                c -> onSortOrderChanged());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> followSelection());

        // The PRP list lives beside the table rather than on top of it. A popup had to be dismissed
        // before the next project could be looked at, which made comparing two projects' PRPs a
        // sequence of open, read, close, open - and the picker is a browsing window.
        prpPanel = new PrpPanel(settings, false, this::closePrpPanel);
        prpPanel.onExitLeft = () -> table.requestFocus();
        split = new SplitPane(table);
        VBox.setVgrow(split, Priority.ALWAYS);

        counts = new Label();
        counts.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        toast = new Label();
        toast.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
        Toast.bind(toast);

        // The progress line joins the counts BELOW the table rather than sitting on a row of its
        // own above it. "Maven Central: 2839 coordinates known" is a footnote, and a footnote that
        // takes a full row costs a row of projects for the whole life of the window. Both lines say
        // how much of what you are looking at is loaded, so they belong together.
        var status = new HBox(12, counts, progressLabel, toast);
        status.setAlignment(Pos.CENTER_LEFT);

        legendPane = buildLegend();
        var projects = new VBox(6, row1, row2, progressBar, split, status, legendPane);
        projects.setPadding(new Insets(10));

        tabs = new PickerTabs(projects, settings, () -> stage, browsePaths, this::knownProjectDirs,
                () -> Platform.runLater(searchField::requestFocus), this::activateProject);
        addTabChips();
        var root = new VBox(tabs.pane);
        VBox.setVgrow(tabs.pane, Priority.ALWAYS);
        Keys.noMnemonics(root);
        // The selection lives in the table while the focus lives in the filter box, so the
        // unfocused selection colour is the one the user actually reads - the default light grey
        // makes the highlighted row nearly invisible for the whole normal life of this dialog.
        root.setStyle("-fx-selection-bar-non-focused: #BBDEFB;");

        // Tracked here rather than asked of the Stage, so the summon key can decide toggle-or-open
        // from its own thread. Bound once, at build, and so covers every way the window opens or
        // closes - Esc, a selection, the hotkey, or the daemon shutting down.
        stage.showingProperty().addListener((o, a, nowShowing) -> PickerHost.showing(nowShowing));

        var scene = new Scene(root);
        // The transcripts pane paints by style class, so its stylesheet has to be on this scene.
        // It is deliberately near-default and touches nothing outside that tab - a warm-paper
        // palette across the whole window was tried and dropped: it read as sepia and fought the
        // pastel action chips, which are how a project row is actually read.
        littlejlib.proj.cct.ui.Theme.applyLight(scene);
        // One filter at the scene, so a function key reaches its action from any focus owner. Bound
        // here rather than per-control because "wherever the focus happens to be" is the entire point.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onGlobalKey);
        // Which pane owns the function keys is decided by the focus, so the window has to SAY where
        // the focus is - otherwise F2 means two things and the user has to guess which.
        scene.focusOwnerProperty().addListener((o, a, b) -> showFocusedPane());
        stage.setScene(scene);
        // Data loading is driven by reopen(), not by onShown: the window is built once and shown
        // many times, and each show needs a fresh load rather than only the first.
    }

    // ---------- the PRP side panel ----------

    boolean prpPanelOpen() {
        // split is assigned late in build(), and a selection listener is live before it
        return split != null && split.getItems().contains(prpPanel.root);
    }

    /**
     * Show the selected project's PRPs beside the table. Opened by a single click on a row, or by
     * F10 - the two ways of saying the same thing with a mouse and without one.
     */
    void openPrpPanel(ProjectRecord rec, boolean focusIt) {
        if (rec == null || rec.isCodeDirNode() || rec.dir() == null) return;
        if (!prpPanelOpen()) {
            split.getItems().add(prpPanel.root);
            // The table keeps the larger share: the panel is the detail view and the list is what
            // is being browsed.
            split.setDividerPositions(0.66);
        }
        prpPanel.showProject(rec.dir());
        // The FILTER, not the list - the same place the focus sits when this window opens, so both
        // panes are entered the same way and Down is always the step from filter into list.
        if (focusIt) Platform.runLater(prpPanel::focusSearch);
        showFocusedPane();
    }

    void closePrpPanel() {
        split.getItems().remove(prpPanel.root);
        table.requestFocus();
        showFocusedPane();
    }

    /** Fades whichever legend is not currently the live one. See {@link PrpPanel#showFocus}. */
    void showFocusedPane() {
        var panelHasIt = prpPanelOpen() && prpPanel.holdsFocus();
        prpPanel.showFocus(panelHasIt);
        legendPane.setOpacity(panelHasIt ? 0.4 : 1.0);
    }

    /**
     * Once open, the panel follows the selection - arrowing down the project list walks the PRPs
     * beside it. Debounced because holding an arrow key down would otherwise list a prp directory
     * per row travelled through, and none of those are the row being travelled to.
     */
    void followSelection() {
        if (!prpPanelOpen()) return;
        if (prpFollowDebounce != null) prpFollowDebounce.stop();
        prpFollowDebounce = new PauseTransition(Duration.millis(180));
        prpFollowDebounce.setOnFinished(e -> {
            var rec = selected();
            if (rec != null && !rec.isCodeDirNode() && rec.dir() != null) prpPanel.showProject(rec.dir());
        });
        prpFollowDebounce.play();
    }

    void onTableClick(javafx.scene.input.MouseEvent e) {
        if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
        if (e.getClickCount() == 2) {
            selectAndClose();
            return;
        }
        // A single click is how a mouse asks "what is in this one", which is what the panel answers.
        // The focus stays on the table so the next click lands where the pointer already is.
        var rec = selected();
        if (rec != null && !rec.isCodeDirNode()) openPrpPanel(rec, false);
    }

    // ---------- loading ----------

    void loadThenMaybeScan() {
        new Thread(() -> {
            var t0 = System.currentTimeMillis();
            central.warmFromCache();
            var cached = new ArrayList<ProjectRecord>();
            browsePaths.forEach(p -> cached.addAll(store.load(p)));
            var deps = store.allDependencies();
            var ms = System.currentTimeMillis() - t0;

            Platform.runLater(() -> {
                records = cached;
                rebuildIndex(deps);
                repopulate();
                if (!cached.isEmpty()) progressLabel.setText("loaded " + cached.size() + " from cache in " + ms + " ms");
                // Always rescan, never only when the cache is empty: a cache that is merely fast
                // is worthless if it is also wrong. The cached rows are on screen within a second
                // and the fresh ones replace them when they arrive, under whatever filter, sort
                // and selection the user has moved to in the meantime.
                rescan(browsePaths);
            });
        }, "Picker-Load").start();
    }

    void rescan() {
        rescan(browsePaths);
    }

    void rescan(List<Path> paths) {
        refreshButton.setDisable(true);
        progressBar.setVisible(true);
        progressLabel.setText("Scanning...");

        new Thread(() -> {
            var t0 = System.currentTimeMillis();
            var all = new ArrayList<ProjectRecord>();
            // Primary roots first: the ones you are most likely to be waiting for finish soonest.
            var ordered = paths.stream()
                    .sorted(java.util.Comparator.comparingInt(p -> typeFor((Path) p).ordinal())).toList();
            for (var p : ordered) {
                var scanner = new ProjectTreeScanner(settings.getProjectScanningDepth())
                        .baseType(typeFor(p).name())
                        .progress((folders, projects) -> Platform.runLater(() -> {
                            progressBar.setProgress(Math.min(folders / 1500.0, 0.95));
                            progressLabel.setText(p + " - " + folders + " folders, " + projects + " projects");
                        }));
                var scanned = scanner.scan(p);
                store.replaceAll(p, scanned, scanner.dependencies(), System.currentTimeMillis());
                all.addAll(scanned);
            }
            var ms = System.currentTimeMillis() - t0;
            var rescanned = ordered.stream().map(p -> p.toAbsolutePath().toString()).toList();
            var deps = store.allDependencies();

            Platform.runLater(() -> {
                // Only the rescanned roots are replaced; rows from roots we did not touch stay.
                var merged = new ArrayList<>(records.stream()
                        .filter(r -> !rescanned.contains(r.codeDir())).toList());
                merged.addAll(all);
                records = merged;
                rebuildIndex(deps);
                repopulate();
                progressBar.setVisible(false);
                var folders = all.stream().filter(ProjectRecord::isFolder).count();
                progressLabel.setText("scanned " + (all.size() - folders) + " projects, "
                        + folders + " folders, " + deps.size() + " dependencies in " + ms + " ms");
                refreshButton.setDisable(false);
                // Deliberately does not grab focus: this finishes while the user is already
                // typing or arrowing through results.
                startWatching();
                sweepCentral();
            });
        }, "Picker-Rescan").start();
    }

    /**
     * Asks Maven Central about every coordinate in the index rather than only the visible ones.
     * <p>
     * The column alone is viewport-lazy, which is right for painting a cell and wrong for a
     * question asked of the whole set: "which of my projects are not published" cannot be answered
     * from the forty rows somebody happened to scroll past. Nothing here blocks - the answers land
     * as they arrive and the concern columns are recomputed when they do.
     */
    void sweepCentral() {
        var coordinates = index.allCoordinates();
        if (coordinates.isEmpty()) return;

        central.sweep(coordinates,
                (done, total) -> Platform.runLater(() -> {
                    progressLabel.setText("Maven Central: " + done + " of " + total + " looked up");
                    if (done % 100 == 0) refreshConcerns();
                }),
                () -> Platform.runLater(() -> {
                    progressLabel.setText("Maven Central: " + coordinates.size() + " coordinates known");
                    refreshConcerns();
                    repopulate();
                }));
    }

    void rebuildIndex(List<littlejlib.proj.model.ProjectDependency> deps) {
        index = new DependencyIndex(records, deps);
        refreshConcerns();
    }

    /**
     * Recomputed on data changes only, never per keystroke: the filter runs on every character typed
     * and walking every project's dependency list at that rate would make typing feel heavy for an
     * answer that cannot have changed between two letters.
     */
    void refreshConcerns() {
        var out = new HashMap<String, ProjectConcerns>(records.size() * 2);
        for (var r : records) {
            // A plain folder has no coordinates, no dependencies and nothing to publish, so every
            // concern about it is vacuous - and the filter already reads a missing entry as "no
            // finding", which is the right answer rather than a computed row of zeroes.
            if (r.isCodeDirNode() || r.isFolder()) continue;
            out.put(r.path(), ProjectConcerns.of(r, index, central::statusOf));
        }
        concerns = out;
        table.refresh();
    }

    ProjectConcerns concernsOf(ProjectRecord rec) {
        return rec == null ? null : concerns.get(rec.path());
    }

    /**
     * Where every indexed project is, for the global PRP tab to stat rather than go hunting.
     * <p>
     * Plain folders included, and not by accident: a directory with no pom and no git can still
     * hold a prp folder, and quite often that is exactly what a project at the specification stage
     * IS. Filtering to build systems here would hide the PRPs of everything not yet started.
     */
    List<Path> knownProjectDirs() {
        return records.stream().filter(r -> !r.isCodeDirNode()).map(ProjectRecord::dir)
                .filter(java.util.Objects::nonNull).toList();
    }

    // ---------- painting ----------

    void repopulate() {
        // Whatever the user had highlighted survives a background refresh landing under them.
        var keep = selected() == null ? null : selected().path();

        var typeOf = ProjectFilter.resolver(settings);
        var show = showBox.getValue() == null ? ConcernFilter.ALL : showBox.getValue();
        var filter = ProjectFilter.of(searchField.getText(), mavenOnly.isSelected(), showFolders.isSelected(),
                        includedTypes(), typeOf)
                .and(rec -> show.test(rec, concernsOf(rec)));
        var order = sortBox.getValue() == null
                ? ProjectSort.RELEVANCE.comparator(Map.of(), r -> false)
                : sortBox.getValue().comparator(ProjectSort.orgWeights(records),
                        r -> typeOf.apply(r) != CodeRepoBaseType.PRIMARY);

        table.setRoot(ProjectTreeBuilder.build(records, filter, order, browsePaths.size() > 1));
        // setRoot drops the applied sort, so a column sort has to be reasserted or every background
        // rescan would silently throw the user's chosen order away.
        if (!table.getSortOrder().isEmpty()) table.sort();

        if (!reselect(keep) && table.getExpandedItemCount() > 0) table.getSelectionModel().select(0);
        updateStatusLine();
    }

    /**
     * The shortcut legend, as chips carrying the SAME colour as the button each one drives.
     * <p>
     * A key printed only in a line of grey text at the bottom of the window is read by looking away
     * from the button, matching a word, and looking back. Wearing the key on the button removes that
     * trip for the buttons that are on screen; colouring the legend to match removes it for the rest.
     * <p>
     * Three things per chip - key, icon, and the name written out in full. The row buttons have to
     * abbreviate because nine of them share a cell; this line does not, and it is the one place
     * where "CpDep" gets to say what it actually means. The icon is the hinge between the two: it
     * is the same glyph in both places, so the legend can be read once and the buttons recognised
     * by shape from then on.
     */
    /**
     * The legend, and the toolbar - see {@link Legend}. Every entry that names an action runs
     * the SAME method its key does.
     * <p>
     * THE TAB ENTRIES ARE GENERATED FROM THE ACTUAL TABS. They were hand-written, and by the
     * time a second group existed they were lying: the strip had grown to six and the legend
     * still promised Alt+3 transcripts and Alt+4 settings. A legend built by walking the thing
     * it describes cannot drift from it.
     */
    javafx.scene.layout.FlowPane buildLegend() {
        var pane = new javafx.scene.layout.FlowPane(4, 3);
        pane.getChildren().add(Chip.of("up/down move, up at top returns to filter", Palette.GREY));
        pane.getChildren().add(Chip.of("right = PRPs of this project, left comes back", Palette.GREY));
        pane.getChildren().add(Chip.of("up from the filter reaches the tabs", Palette.GREY));
        pane.getChildren().add(Chip.of("Enter select", Palette.GREY));
        pane.getChildren().add(Chip.of("a-z filter", Palette.GREY));
        pane.getChildren().add(Chip.of("right-click row for these + open in", Palette.GREY));
        for (var a : ProjectAction.values()) {
            pane.getChildren().add(Legend.withTip(
                    Legend.of(a.legendText(), a.style, a.icon, a.iconColor, () -> runAction(a)),
                    a.tooltip));
        }
        pane.getChildren().add(Legend.of("F11 menu", Palette.GREY, this::openMenu));
        pane.getChildren().add(Legend.of("F12 refresh", Palette.GREY, this::rescan));
        tabChipAt = pane.getChildren().size();
        // The tab chips land here later - see addTabChips.
        pane.getChildren().add(Chip.of("row colour = worked on recently", Palette.GREY));
        // Only when one is actually bound. A legend entry for a gesture the daemon did not install
        // would be teaching a key that silently does nothing, which is worse than not saying.
        if (!littlejlib.proj.util.GlobalHotkey.isOff(settings.getGlobalHotkey())) {
            pane.getChildren().add(Chip.of(
                    littlejlib.proj.util.GlobalHotkey.describe(settings.getGlobalHotkey())
                            + " summons this window", Palette.GREY));
        }
        pane.getChildren().add(Legend.of("Alt+M maven", Palette.GREY, () -> toggle(mavenOnly, "Maven only")));
        pane.getChildren().add(Legend.of("Alt+F folders", Palette.GREY, () -> toggle(showFolders, "Plain folders")));
        pane.getChildren().add(Legend.of("Alt+G mirror", Palette.GREY, () -> toggle(includeMirror, "GitHub mirror")));
        pane.getChildren().add(Legend.of("Alt+A auxiliary", Palette.GREY, () -> toggle(includeAuxiliary, "Auxiliary")));
        pane.getChildren().add(Legend.of("Alt+B add base", Palette.GREY, this::addCodeRepoBase));
        pane.getChildren().add(Legend.of("Alt+N new folder", Palette.GREY, this::newFolder));
        pane.getChildren().add(Legend.of("Alt+K project key", Palette.GREY, this::editProjectKey));
        pane.getChildren().add(Chip.of("Esc cancel", Palette.GREY));
        return pane;
    }

    void updateStatusLine() {
        var sorted = table.getSortOrder().isEmpty() ? "" : "   (sorted by column - click the header again to clear)";
        var visible = visibleCounts();
        var split = visible[1] == 0 ? "" : "   (" + visible[0] + " projects, " + visible[1] + " folders)";
        counts.setText((visible[0] + visible[1]) + " of " + records.size() + " shown"
                + split + excludedNote() + sorted);
    }

    // ---------- keyboard ----------

    /**
     * Function keys and the Alt toggles, handled once for the whole scene so they work from the
     * filter box, the table, or anywhere else the focus has ended up. A scene filter runs before the
     * controls see the key, which is what keeps the table's own type-ahead out of the way.
     */
    void onGlobalKey(KeyEvent e) {
        if (e.isAltDown()) { onAltKey(e); return; }
        if (e.isControlDown() || e.isMetaDown()) return;

        // THE TAB STRIP IS PART OF EVERY TAB'S ARROW MODEL, so it is handled HERE - before anything
        // that knows which tab is showing. Wiring it per pane is what made the navigation stop
        // working the moment you left the projects table: from the PRP search box there was no way
        // back up, and the transcripts and settings panes had never heard of it at all. A rule that
        // holds on one tab and not the others is worse than no rule, because it is learned and then
        // betrayed.
        if (tabs.stripHasFocus() && tabs.onStripArrow(e)) return;
        if (Keys.plain(e, KeyCode.UP) && atTopOfPane()) {
            tabs.focusStrip();
            e.consume();
            return;
        }

        // Off the Projects tab the picker's keys mean nothing. The global PRP list is a PrpPanel and
        // the transcripts pane is a whole second keyboard model; each wants its own. Esc anywhere
        // else is the way back to the projects rather than a way to cancel a selection that is not
        // being made - but only when the tab itself did not want it, since Esc backs out of a find
        // bar and a turn list before it backs out of the tab.
        if (!tabs.codeProjectsShowing()) {
            // The strip owns Left/Right/Down while it holds the focus - the top of the arrow rule.
            if (tabs.stripHasFocus() && tabs.onStripArrow(e)) return;
            var group = tabs.selectedGroup();
            if (group != null && group.prps) group.prpPanel.onKey(e);
            if (group != null && group.folders != null && group.folders.onKey(e)) return;
            if (tabs.selected() == tabs.transcripts) tabs.transcriptsTab.onKey(e);
            if (tabs.selected() == tabs.conversations) tabs.conversationsTab.onKey(e);
            if (!e.isConsumed() && Keys.plain(e, KeyCode.ESCAPE)) {
                tabs.select(tabs.projects);
                e.consume();
            }
            return;
        }

        // THE ONE AMBIGUITY IN THIS WINDOW, and how it is resolved. The PRP panel binds the same
        // function keys as the projects table - F2 opens a file there and a folder here - because
        // both sets are older than the panel and changing either would break the muscle memory of
        // the popup that still exists. So the focus decides, the focused pane wears a coloured
        // header and its own legend, and nothing is left to be worked out.
        //
        // A key the panel does not want FALLS THROUGH rather than being swallowed, which is what
        // keeps F10 and F11 reachable from inside it. Esc does not fall through, and should not:
        // it closes the panel, which is one step back rather than out of the window.
        if (prpPanelOpen() && prpPanel.holdsFocus()) {
            prpPanel.onKey(e);
            if (e.isConsumed()) return;
        }

        onArrowKey(e);
        if (e.isConsumed()) return;

        var code = e.getCode();
        if (code == KeyCode.F11) { openMenu(); e.consume(); return; }
        if (code == KeyCode.F12) { rescan(); e.consume(); return; }

        var action = ProjectAction.byKey(code);
        if (action == null) return;
        e.consume();
        runAction(action);
    }

    /**
     * One entry point for an action however it was asked for - the function key, the row menu, or a
     * click on its legend chip. The legend is a complete list of what this window does, which makes
     * it a toolbar; having it call this rather than its own copy is what stops the two drifting.
     */
    void runAction(ProjectAction action) {
        var rec = selected();
        if (rec == null || rec.isCodeDirNode()) {
            Toast.show("select a project first");
            return;
        }
        if (!action.supportedBy(rec)) {
            Toast.show(rec.name() + " does not support " + action.label + " (" + action.keyName() + ")");
            return;
        }
        onAction(action, rec);
    }

    /**
     * Is the focus in a filter box - the top of whichever pane is showing?
     * <p>
     * Answered by asking the FOCUS OWNER rather than by naming the fields, so it holds for panes
     * this class has never heard of: the PRP search, the transcripts search, a settings field, and
     * whatever comes next. A one-line text field has no use for Up - the caret cannot go anywhere -
     * so the key is free to mean "leave the pane upward" everywhere in this window at once.
     */
    boolean atTopOfPane() {
        var owner = stage.getScene() == null ? null : stage.getScene().getFocusOwner();
        return owner instanceof javafx.scene.control.TextField
                || owner instanceof javafx.scene.control.ComboBox<?>;
    }

    /**
     * MOVING BETWEEN THE PANES, and within one, with nothing but the four arrows.
     * <p>
     * The rule is spatial and has no exceptions: <b>the arrow that points out of a pane leaves
     * it</b>. The projects table is the left-hand pane, so Right goes to the PRP panel, opening it
     * if it was closed; the panel is the right-hand one, so Left comes back. Nothing has to be
     * remembered beyond where the two panes are on the screen.
     * <p>
     * Down and Up are the same shape in both: the filter is the top of the pane, Down from it enters
     * the list, and Up off the top of the list returns to it. So the filter is always exactly one Up
     * away from wherever you are, in either pane, which is the state this window spends its life in.
     * <p>
     * The cost, stated plainly: Right no longer expands a node in the projects tree. It is a small
     * cost because {@code ProjectTreeBuilder} expands every node it builds, so there is nothing
     * collapsed to open unless the user collapsed it with Left. Left still collapses, and the tree
     * inside the PRP panel keeps both, since Right is not the outward arrow over there.
     */
    void onArrowKey(KeyEvent e) {
        if (e.isAltDown() || e.isControlDown() || e.isShiftDown() || e.isMetaDown()) return;

        switch (e.getCode()) {
            case RIGHT -> {
                // Not from the filter box: Right there is moving the caret through what is being
                // typed, and a caret that jumps to another pane mid-word would be unusable.
                if (!table.isFocused()) return;
                var rec = selected();
                if (rec == null || rec.isCodeDirNode()) {
                    Toast.show("select a project to see its PRPs");
                } else {
                    openPrpPanel(rec, false);
                    Platform.runLater(prpPanel::focusSearch);
                }
                e.consume();
            }
            case UP -> {
                // Up out of the filter box is NOT handled here - onGlobalKey owns it for every pane
                // in the window at once, so that the rule cannot hold on this tab and nowhere else.
                if (!table.isFocused()) return;
                if (table.getSelectionModel().getSelectedIndex() > 0) return;
                searchField.requestFocus();
                searchField.positionCaret(searchField.getText() == null
                        ? 0 : searchField.getText().length());
                e.consume();
            }
            default -> { }
        }
    }

    /**
     * The view toggles. Alt because every bare key is already committed elsewhere, and these are the
     * infrequent ones - a scope is set once and then typed against for the rest of the session.
     */
    void onAltKey(KeyEvent e) {
        // The tabs first, because they work from every tab and the rest do not.
        if (tabs.onAltDigit(e)) return;
        // Alt+N is the same action on every tab, so a plain group answers it itself.
        var plain = tabs.selectedGroup();
        if (plain != null && plain.folders != null && Keys.alt(e, KeyCode.N)) {
            plain.folders.newFolder();
            e.consume();
            return;
        }
        if (tabs.selected() == tabs.transcripts) { tabs.transcriptsTab.onKey(e); return; }
        // Before the code-projects guard: a key belongs to a project, and a PRP tab is showing PRPs
        // OF a project - which is exactly where the ticket it produces is on screen.
        if (Keys.alt(e, KeyCode.K)) { editProjectKey(); e.consume(); return; }
        if (!tabs.codeProjectsShowing()) return;
        if (Keys.alt(e, KeyCode.M)) toggle(mavenOnly, "Maven only");
        else if (Keys.alt(e, KeyCode.F)) toggle(showFolders, "Plain folders");
        else if (Keys.alt(e, KeyCode.G)) toggle(includeMirror, "GitHub mirror");
        else if (Keys.alt(e, KeyCode.A)) toggle(includeAuxiliary, "Auxiliary");
        else if (Keys.alt(e, KeyCode.B)) addCodeRepoBase();
        else if (Keys.alt(e, KeyCode.N)) newFolder();
        else return;
        e.consume();
    }

    /**
     * An empty directory inside the selected one - where a new project starts before it has a pom.
     * <p>
     * Alt rather than a function key because F1..F12 are all spoken for in this window, and Alt is
     * for the infrequent; a folder is created once per project. The same key does the same thing on
     * the plain tabs, which is the point of not inventing a second one there.
     */
    void newFolder() {
        var rec = selected();
        var parent = NewFolder.parentFor(rec == null ? null : rec.dir(),
                browsePaths.isEmpty() ? null : browsePaths.get(0));
        if (NewFolder.createUnder(parent) != null) rescan();
    }

    /**
     * The project whose key Alt+K edits: whatever the visible PRP list has selected, else the
     * selected project row. A PRP row knows its own project, which is what makes this work on a PRP
     * tab and in the side panel without asking the user to go and select the project first.
     */
    Path projectForKey() {
        var group = tabs.selectedGroup();
        if (group == null) return null;                       // transcripts, settings

        // A PRP row knows its own project, which is what lets this work on a PRP tab without
        // sending the user back to the rows tab to select the project first.
        if (group.prps) return projectOf(group.prpPanel);

        // A PLAIN group has its own rows and its own PRP side panel. Falling through to the CODE
        // table from here is what made Alt+K on a legal case rename a maven project on a tab that
        // was not even showing.
        if (group.folders != null) {
            var fromPanel = group.folders.prpOpen() ? projectOf(group.folders.prpPanel) : null;
            return fromPanel != null ? fromPanel : group.folders.selectedDir();
        }

        if (prpPanelOpen() && prpPanel.holdsFocus()) {
            var fromPanel = projectOf(prpPanel);
            if (fromPanel != null) return fromPanel;
        }
        var rec = selected();
        return rec == null || rec.isCodeDirNode() ? null : rec.dir();
    }

    static Path projectOf(PrpPanel panel) {
        var node = panel == null ? null : panel.selected();
        return node == null ? null : node.prp.getProjectPath();
    }

    void editProjectKey() {
        ProjectKeyDialog.show(stage, projectForKey(), this::refreshTickets);
    }

    /** Every PRP list is showing tickets built from that key, so all of them are now out of date. */
    void refreshTickets() {
        if (prpPanelOpen()) prpPanel.reload();
        for (var g : tabs.groups) {
            if (g.prpPanel != null) g.prpPanel.reload();
            if (g.folders != null && g.folders.prpOpen()) g.folders.prpPanel.reload();
        }
    }

    /** The focus deliberately stays where it was - the tick alone is easy to miss, so it is said. */
    void toggle(CheckBox box, String name) {
        box.setSelected(!box.isSelected());
        Toast.show(name + (box.isSelected() ? " on" : " off"));
    }

    void onSearchKey(KeyEvent e) {
        switch (e.getCode()) {
            case ESCAPE -> { cancel(); e.consume(); }
            case ENTER -> { selectAndClose(); e.consume(); }
            // The arrows drive the table while the caret stays where it is. Focus follows, because
            // the table has to own the keys to scroll itself; typing a letter brings it straight
            // back, so from the user's side the caret never appears to have left.
            case DOWN -> { focusTable(); move(1); e.consume(); }
            // Up in the filter does NOTHING, deliberately. The filter is the top of this pane, and
            // Up off the top of the list comes back to it - so an Up here that jumped into the list
            // made the two keys a round trip that ended where it started.
            case UP -> e.consume();
            case PAGE_DOWN -> { focusTable(); move(20); e.consume(); }
            case PAGE_UP -> { focusTable(); move(-20); e.consume(); }
            default -> { }
        }
    }

    void onTableKey(KeyEvent e) {
        var code = e.getCode();
        if (code == KeyCode.ESCAPE) { cancel(); e.consume(); return; }
        if (code == KeyCode.ENTER) { selectAndClose(); e.consume(); return; }

        // Typing anywhere means typing into the filter. Without this the letters would be eaten by
        // the table's own type-ahead, which selects a row by prefix and is a different, worse
        // search than the one this dialog already has.
        if (code == KeyCode.BACK_SPACE) {
            var text = searchField.getText();
            searchField.requestFocus();
            if (text != null && !text.isEmpty()) searchField.setText(text.substring(0, text.length() - 1));
            searchField.positionCaret(searchField.getText().length());
            e.consume();
            return;
        }

        var typed = e.getText();
        if (typed == null || typed.isEmpty() || e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;
        if (typed.codePointAt(0) < 0x20) return;      // control characters are not text

        searchField.requestFocus();
        searchField.appendText(typed);
        searchField.positionCaret(searchField.getText().length());
        e.consume();
    }

    void move(int delta) {
        var n = table.getExpandedItemCount();
        if (n == 0) return;
        var current = table.getSelectionModel().getSelectedIndex();
        var next = current < 0 ? 0 : Math.max(0, Math.min(n - 1, current + delta));
        table.getSelectionModel().clearAndSelect(next);
        table.scrollTo(Math.max(0, next - 3));
    }

    void focusTable() {
        table.requestFocus();
        if (table.getSelectionModel().getSelectedIndex() < 0 && table.getExpandedItemCount() > 0) {
            table.getSelectionModel().select(0);
        }
    }

    void onSortOrderChanged() {
        if (syncingSort) return;
        syncingSort = true;
        try {
            // A column sort and the Sort box are two answers to one question. The column wins while
            // it is set, and the box is greyed rather than left showing a value it is not applying.
            var byColumn = !table.getSortOrder().isEmpty();
            sortBox.setDisable(byColumn);
            updateStatusLine();
        } finally {
            syncingSort = false;
        }
    }

    // ---------- actions ----------

    ProjectRecord selected() {
        var item = table.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    /**
     * Right-click a row for its actions. The button column is off by default now, so this and the
     * function keys are the two ways in: the pointer for the row under it, the keys for the row that
     * is selected.
     * <p>
     * The row handler consumes, so the table's own handler only ever sees the keyboard's Menu key -
     * which is delivered to the focus owner, and that is the table rather than any row. Right-click
     * also moves the selection onto the row it opened, so pressing a function key straight after the
     * menu acts on what the menu was about rather than on wherever the selection had been left.
     */
    void installRowMenu() {
        table.setRowFactory(tv -> {
            var row = new ProjectHeatRow();
            row.setOnContextMenuRequested(e -> {
                if (!row.isEmpty()) table.getSelectionModel().select(row.getIndex());
                showRowMenu(row.getItem(), e.getScreenX(), e.getScreenY());
                e.consume();
            });
            return row;
        });
        table.setOnContextMenuRequested(e -> showRowMenu(selected(), e.getScreenX(), e.getScreenY()));
    }

    void showRowMenu(ProjectRecord rec, double screenX, double screenY) {
        if (rowMenu != null) rowMenu.hide();
        if (rec == null || rec.isCodeDirNode()) return;
        rowMenu = ActionContextMenu.of(ProjectPickerColumns.BAR, ides, rec, this::onAction, this::onLaunch);
        rowMenu.show(table, screenX, screenY);
    }

    void onAction(ProjectAction action, ProjectRecord rec) {
        if (rec == null || !action.supportedBy(rec)) return;
        switch (action) {
            case TERMINAL_CD -> {
                recordUsage(rec);
                result = new PickerResult(rec, Action.NONE);
                stage.close();
            }
            case PIN_JACLI -> Toast.show("jacli pinning arrives in phase D");
            case DEPENDENCIES -> DependenciesDialog.show(stage, rec, index, central::statusOf);
            // A toggle, because the panel is now the answer and F10 is the only key that can put
            // it away when the focus is on the table - Esc there cancels the picker instead.
            case PRP -> {
                if (prpPanelOpen() && rec.dir() != null && rec.dir().equals(prpPanel.projectDir)) {
                    closePrpPanel();
                } else {
                    openPrpPanel(rec, true);
                }
            }
            default -> ProjectActions.perform(action, rec, settings);
        }
    }

    void onLaunch(littlejlib.proj.ide.DetectedIde ide, ProjectRecord rec) {
        recordUsage(rec);
        ProjectActions.launch(rec, ide.command());
        Toast.show("Opening " + rec.name() + " in " + ide.name);
    }

    void openPrpManager() {
        var rec = selected();
        if (rec == null || rec.isCodeDirNode()) {
            Toast.show("select a project first");
            return;
        }
        openPrpPanel(rec, true);
    }

    void openMenu() {
        PickerMenuDialog.show(stage, selected(), settings, this::rescan, this::addCodeRepoBase,
                this::startAgentOnPrp, this::openSettings);
    }

    void openSettings() {
        if (SettingsDialog.showOnFxThread(stage, settings)) {
            // The IDE and terminal columns are built from settings at construction time, so a
            // change to either has to be reflected rather than waiting for the next daemon restart.
            littlejlib.proj.ide.IdeDetector.forget();
            littlejlib.proj.terminal.TerminalDetector.forget();
            // SettingsDialog has already marked this window stale, so "reopen" is now true advice
            // rather than the suggestion it used to be - the next open rebuilds from the new file.
            Toast.show("Settings saved - reopen the picker for column changes to take effect");
        }
    }

    /**
     * Picks a PRP of the selected project and starts the agent on it, from the project root.
     * <p>
     * A chooser rather than "the newest": the PRP you want is usually the one you just made, but not
     * always, and guessing wrong starts a long-running agent on the wrong specification.
     */
    void startAgentOnPrp() {
        var rec = selected();
        if (rec == null || rec.isCodeDirNode()) {
            Toast.show("select a project first");
            return;
        }
        AgentStarter.chooseAndStart(stage, rec.dir(), settings);
    }

    void selectAndClose() {
        var rec = selected();
        if (rec == null || rec.isCodeDirNode()) return;
        recordUsage(rec);
        result = new PickerResult(rec, Action.NONE);
        stage.close();
    }

    /** Picking a project is the signal that ranks it next time. Written off the UI thread. */
    void recordUsage(ProjectRecord rec) {
        var path = rec.path();
        rec.openCount(rec.openCount() + 1).lastOpened(System.currentTimeMillis());
        new Thread(() -> store.recordUsage(path), "Picker-Usage").start();
    }

    void cancel() {
        result = null;
        stage.close();
    }

    // ---------- coderepobases, watching, counting ----------

    CodeRepoBaseType typeFor(Path p) {
        return settings.typeOf(p);
    }

    /** Adding a coderepobase from the UI. The type is chosen explicitly - there is no default. */
    void addCodeRepoBase() {
        var chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Choose a coderepobase - a directory holding many repositories");
        var dir = chooser.showDialog(stage);
        if (dir == null) return;

        var choice = new ChoiceDialog<>(CodeRepoBaseType.PRIMARY, CodeRepoBaseType.values());
        choice.setTitle("Add coderepobase");
        choice.setHeaderText(dir.getAbsolutePath());
        choice.setContentText("What kind is it?");
        AppIcon.on(choice);
        var type = choice.showAndWait().orElse(null);
        if (type == null) return;

        settings.addCodeRepoBase(dir.getAbsolutePath(), type);
        new SettingsManager().saveSettings(settings);
        Toast.show("Added " + type.label + " coderepobase: " + dir.getName()
                + " - reopen the picker to scan it");
    }

    /**
     * Watching only starts once a full scan has established the tree, so the watcher registers
     * against directories that are already known rather than racing the scan.
     */
    void startWatching() {
        if (watcher != null) return;
        // Only the primary coderepobase is watched. A mirror changes when you clone into it and an
        // archive barely changes at all, so a watch registration per directory across them buys
        // notifications nobody is waiting on.
        var watchable = browsePaths.stream().filter(p -> typeFor(p).watched()).toList();
        if (watchable.isEmpty()) return;
        watcher = new ProjectWatcher(settings.getProjectScanningDepth()).onChange(this::onWatchedChange);
        new Thread(() -> watcher.start(watchable), "Picker-WatchStart").start();
    }

    void onWatchedChange(List<ProjectRecord> changed) {
        Platform.runLater(() -> {
            var byPath = new java.util.LinkedHashMap<String, ProjectRecord>();
            records.forEach(r -> byPath.put(r.path(), r));
            changed.forEach(r -> byPath.put(r.path(), r));
            records = new ArrayList<>(byPath.values());
            rebuildIndex(store.allDependencies());
            repopulate();
            Toast.show(changed.size() == 1
                    ? "updated " + changed.get(0).name()
                    : "updated " + changed.size() + " projects");
        });
    }

    java.util.Set<CodeRepoBaseType> includedTypes() {
        var set = java.util.EnumSet.of(CodeRepoBaseType.PRIMARY);
        if (includeMirror.isSelected()) set.add(CodeRepoBaseType.GITHUB_MIRROR);
        if (includeAuxiliary.isSelected()) set.add(CodeRepoBaseType.AUXILIARY);
        return set;
    }

    /** "3 more in GitHub mirror" - so an excluded base never looks like an absent project. */
    String excludedNote() {
        var included = includedTypes();
        var typeOf = ProjectFilter.resolver(settings);
        var textMatch = ProjectFilter.textOnly(searchField.getText(), mavenOnly.isSelected(),
                showFolders.isSelected());
        var parts = new ArrayList<String>();
        for (var type : CodeRepoBaseType.values()) {
            if (included.contains(type)) continue;
            var n = records.stream().filter(textMatch).filter(r -> typeOf.apply(r) == type).count();
            if (n > 0) parts.add(n + " in " + type.label);
        }
        return parts.isEmpty() ? "" : "   (" + String.join(", ", parts) + " - tick to include)";
    }

    /**
     * Projects and plain folders on screen, counted apart. The synthetic code-directory rows are
     * scaffolding rather than results and count as neither.
     * <p>
     * Two numbers because they answer two questions: a folder row is a place to go, a project row is
     * something that builds, and one total would hide which of the two the filter is actually finding.
     */
    int[] visibleCounts() {
        var projects = 0;
        var folders = 0;
        for (var i = 0; i < table.getExpandedItemCount(); i++) {
            var item = table.getTreeItem(i);
            var rec = item == null ? null : item.getValue();
            if (rec == null || rec.isCodeDirNode()) continue;
            if (rec.isFolder()) folders++;
            else projects++;
        }
        return new int[]{projects, folders};
    }

    /**
     * "Show me this project", asked by the Transcripts tab about the folder a conversation ran in.
     * <p>
     * This is what the old note in the PRP called "maybe not directly possible": as a separate
     * executable the only way to reach proj was to shell out to it. In one process it is a tab
     * switch and a row selection. Falls back to typing the folder name into the filter, because the
     * row may be hidden by whatever filter is currently set, and says so if it is simply not indexed.
     */
    void activateProject(Path dir) {
        if (dir == null) return;
        var path = dir.toAbsolutePath().toString();

        // WHICH TAB the directory belongs to, before assuming the code one. Roughly half the
        // sessions on this machine ran in a plain group, and every one of them used to land on
        // "not in the project index - proj scan would pick it up" - advice that was simply false,
        // because no code scan was ever going to find a folder outside the code bases.
        var group = littlejlib.proj.group.Groups.holding(settings, dir);
        if (group != null && !group.isCode()) {
            var tab = tabs.groups.stream()
                    .filter(g -> !g.prps && g.group.slug().equals(group.slug()))
                    .findFirst().orElse(null);
            if (tab != null && tab.folders != null) {
                tabs.select(tab.tab);
                Platform.runLater(() -> {
                    if (tab.folders.reselect(path)) Toast.show(dir.getFileName() + " selected");
                    else Toast.show(dir + " is not indexed yet - F12 on this tab rescans it");
                });
                return;
            }
        }
        tabs.select(tabs.projects);
        Platform.runLater(() -> {
            if (reselect(path)) {
                table.requestFocus();
                Toast.show(dir.getFileName() + " selected");
                return;
            }
            searchField.setText(dir.getFileName().toString());
            Platform.runLater(() -> {
                if (reselect(path)) {
                    table.requestFocus();
                    Toast.show(dir.getFileName() + " selected");
                } else {
                    Toast.show(dir + " is not in the project index - proj scan would pick it up");
                }
            });
        });
    }

    /**
     * One chip per tab, generated from the tabs themselves and inserted where the legend left room.
     * <p>
     * Generated rather than written out, because the hand-written version was already lying by the
     * time a second group existed: it promised Alt+3 transcripts and Alt+4 settings while the strip
     * had grown to six. A legend that walks the thing it describes cannot say a key that is not
     * bound.
     */
    void addTabChips() {
        var all = tabs.pane.getTabs();
        for (var i = 0; i < all.size() && i < 9; i++) {
            var tab = all.get(i);
            legendPane.getChildren().add(Math.min(tabChipAt + i, legendPane.getChildren().size()),
                    Legend.of("Alt+" + (i + 1) + " " + tab.getText().toLowerCase(),
                            Palette.GREY, () -> tabs.select(tab)));
        }
    }

    boolean reselect(String path) {
        if (path == null) return false;
        for (var i = 0; i < table.getExpandedItemCount(); i++) {
            var item = table.getTreeItem(i);
            if (item != null && item.getValue() != null && path.equals(item.getValue().path())) {
                table.getSelectionModel().select(i);
                table.scrollTo(Math.max(0, i - 3));
                return true;
            }
        }
        return false;
    }

    void scheduleRepaint() {
        Platform.runLater(() -> {
            if (repaintDebounce != null) repaintDebounce.stop();
            repaintDebounce = new PauseTransition(Duration.millis(400));
            repaintDebounce.setOnFinished(e -> table.refresh());
            repaintDebounce.play();
        });
    }

    // ---------- lifecycle ----------

    /** True when this instance already covers the requested roots and can simply be reshown. */
    boolean covers(List<Path> paths) {
        return browsePaths.equals(paths);
    }

    /**
     * Re-show an already-built window. Constructing the first Stage and Scene costs seconds even
     * on a warm JavaFX platform, which is precisely the cost a resident daemon exists to pay once.
     */
    void reopen(Object lock, Object[] holder) { reopen(lock, holder, null); }

    void reopen(Object lock, Object[] holder, String tab) {
        result = null;
        stage.setOnHidden(e -> {
            synchronized (lock) {
                holder[0] = result;
                lock.notifyAll();
            }
        });
        searchField.clear();
        // A reopen is a fresh start: back on the Projects tab, with the detail panel put away.
        // Leaving either as it was would make what the next `proj o` shows depend on what the last
        // one was doing, which is a state nobody is holding in their head half a day later. A named
        // tab overrides that, because `proj cct` asked for one by name.
        tabs.select(tabs.byName(tab));
        closePrpPanel();
        loadThenMaybeScan();
        stage.show();
        stage.toFront();
        stage.requestFocus();
        searchField.requestFocus();

        // Deferred by one pulse, deliberately: FindWindow cannot see a window that show() has only
        // just queued, and a focus request made in the same pulse as show() lands before the scene
        // is realized and is silently dropped. Both matter after a global-key summon, where Windows
        // will not give this process the foreground for the asking - see WindowsForeground.
        Platform.runLater(() -> {
            littlejlib.proj.util.WindowsForeground.grab(TITLE);
            searchField.requestFocus();
        });
    }

    /** Released when the daemon exits, or when a settings change makes this window out of date. */
    void dispose() {
        central.shutdown();
        if (watcher != null) watcher.stop();
        // Only from the FX thread: the other caller is the daemon's shutdown hook, on which
        // touching a Stage throws and would mask whatever it was actually shutting down for.
        if (stage != null && Platform.isFxApplicationThread()) stage.close();
    }

    public static PickerResult show(List<Path> browsePaths, Settings settings) {
        return PickerHost.show(browsePaths, settings, null);
    }

    public static PickerResult show(List<Path> browsePaths, Settings settings, String tab) {
        return PickerHost.show(browsePaths, settings, tab);
    }
}
