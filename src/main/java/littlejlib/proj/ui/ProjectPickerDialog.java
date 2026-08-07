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
 * THE KEYBOARD MODEL, which everything else here bends to accommodate. Three families of key, each
 * owning a job nothing else touches, so no key ever means two things and no context has to be
 * switched to reach one:
 * <ul>
 *   <li>letters and digits type into the filter, from wherever the focus is - press one while the
 *       table has focus and it lands in the filter box, with the focus, mid-word;</li>
 *   <li>arrows, page and home/end move the selection, from wherever the focus is;</li>
 *   <li>function keys run the actions, from wherever the focus is.</li>
 * </ul>
 * The old scheme gave the actions single letters, which meant they were unreachable while typing a
 * filter - the state this dialog spends nearly all its life in - and a digit shortcut for jumping to
 * a row, which collided with every project whose name contains a number. Both are gone. Nothing here
 * needs a modifier key, which is a deliberate constraint rather than an aesthetic one.
 */
public class ProjectPickerDialog {

    public enum Action { NONE, EXECUTED }

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
    CheckBox mavenOnly, includeMirror, includeAuxiliary;
    ChoiceBox<ConcernFilter> showBox;
    ChoiceBox<ProjectSort> sortBox;
    Button addBaseButton, refreshButton, prpButton, menuButton;
    TreeTableView<ProjectRecord> table;
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
        stage.setTitle("Select Project" + (browsePaths.size() == 1 ? " - " + browsePaths.get(0) : ""));

        // Fit the screen actually in front of the user. A fixed 1400x760 overflowed a 1536x864
        // display and took the maven-only checkbox and the whole shortcut line off-screen with it.
        var screen = Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1500, screen.getWidth() - 60));
        stage.setHeight(Math.min(820, screen.getHeight() - 60));
        stage.setX(screen.getMinX() + (screen.getWidth() - stage.getWidth()) / 2);
        stage.setY(screen.getMinY() + (screen.getHeight() - stage.getHeight()) / 2);

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

        mavenOnly = new CheckBox("_Maven only");
        mavenOnly.setTooltip(new Tooltip("Show only maven projects - this is the old mm view"));
        mavenOnly.selectedProperty().addListener((o, a, b) -> repopulate());

        includeMirror = new CheckBox("_GitHub mirror");
        includeMirror.setTooltip(new Tooltip(CodeRepoBaseType.GITHUB_MIRROR.description));
        includeMirror.selectedProperty().addListener((o, a, b) -> repopulate());

        includeAuxiliary = new CheckBox("_Auxiliary");
        includeAuxiliary.setTooltip(new Tooltip(CodeRepoBaseType.AUXILIARY.description));
        includeAuxiliary.selectedProperty().addListener((o, a, b) -> repopulate());

        addBaseButton = new Button("+_Base");
        addBaseButton.setTooltip(new Tooltip("Add a coderepobase - a directory holding many repositories"));
        addBaseButton.setOnAction(e -> addCodeRepoBase());

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
        var row2 = new HBox(8, mavenOnly, includeMirror, includeAuxiliary,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                addBaseButton, prpButton, menuButton, refreshButton);
        row1.setAlignment(Pos.CENTER_LEFT);
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.getChildren().forEach(n -> {
            if (n instanceof CheckBox c) c.setMnemonicParsing(true);
            if (n instanceof Button b) b.setMnemonicParsing(true);
        });

        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(6);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressLabel = new Label();
        progressLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        table = new TreeTableView<>();
        table.setShowRoot(false);
        table.setTableMenuButtonVisible(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        ProjectPickerColumns.install(table, settings, central, this::concernsOf,
                this::scheduleRepaint, this::onAction, this::onLaunch);
        table.setOnKeyPressed(this::onTableKey);
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2) selectAndClose(); });
        table.getSortOrder().addListener((javafx.collections.ListChangeListener<TreeTableColumn<ProjectRecord, ?>>)
                c -> onSortOrderChanged());

        counts = new Label();
        counts.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        toast = new Label();
        toast.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
        Toast.bind(toast);

        var status = new HBox(12, counts, toast);
        status.setAlignment(Pos.CENTER_LEFT);

        var root = new VBox(6, row1, row2, progressBar, progressLabel, table, status, buildLegend());
        root.setPadding(new Insets(10));
        // The selection lives in the table while the focus lives in the filter box, so the
        // unfocused selection colour is the one the user actually reads - the default light grey
        // makes the highlighted row nearly invisible for the whole normal life of this dialog.
        root.setStyle("-fx-selection-bar-non-focused: #BBDEFB;");

        var scene = new Scene(root);
        // One filter at the scene, so a function key reaches its action from any focus owner. Bound
        // here rather than per-control because "wherever the focus happens to be" is the entire point.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onGlobalKey);
        stage.setScene(scene);
        // Data loading is driven by reopen(), not by onShown: the window is built once and shown
        // many times, and each show needs a fresh load rather than only the first.
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
                progressLabel.setText("scanned " + all.size() + " projects, "
                        + deps.size() + " dependencies in " + ms + " ms");
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
            if (r.isCodeDirNode()) continue;
            out.put(r.path(), ProjectConcerns.of(r, index, central::statusOf));
        }
        concerns = out;
        table.refresh();
    }

    ProjectConcerns concernsOf(ProjectRecord rec) {
        return rec == null ? null : concerns.get(rec.path());
    }

    // ---------- painting ----------

    void repopulate() {
        // Whatever the user had highlighted survives a background refresh landing under them.
        var keep = selected() == null ? null : selected().path();

        var typeOf = ProjectFilter.resolver(settings);
        var show = showBox.getValue() == null ? ConcernFilter.ALL : showBox.getValue();
        var filter = ProjectFilter.of(searchField.getText(), mavenOnly.isSelected(), includedTypes(), typeOf)
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
     */
    javafx.scene.layout.FlowPane buildLegend() {
        var pane = new javafx.scene.layout.FlowPane(4, 3);
        pane.getChildren().add(chip("↑↓ move", Palette.GREY));
        pane.getChildren().add(chip("Enter select", Palette.GREY));
        pane.getChildren().add(chip("a-z filter", Palette.GREY));
        for (var a : ProjectAction.values()) {
            pane.getChildren().add(chip(a.keyName() + " " + a.label.toLowerCase(), a.style));
        }
        pane.getChildren().add(chip("F11 menu", Palette.GREY));
        pane.getChildren().add(chip("F12 refresh", Palette.GREY));
        pane.getChildren().add(chip("Esc cancel", Palette.GREY));
        return pane;
    }

    static Label chip(String text, String style) {
        var l = new Label(text);
        l.setStyle("-fx-font-size: 9px; -fx-padding: 1 5 1 5; -fx-background-radius: 3;" + style);
        return l;
    }

    void updateStatusLine() {
        var sorted = table.getSortOrder().isEmpty() ? "" : "   (sorted by column - click the header again to clear)";
        counts.setText(visibleProjectCount() + " of " + records.size() + " shown" + excludedNote() + sorted);
    }

    // ---------- keyboard ----------

    /**
     * Function keys, handled once for the whole scene so they work from the filter box, the table,
     * or anywhere else the focus has ended up.
     */
    void onGlobalKey(KeyEvent e) {
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;

        var code = e.getCode();
        if (code == KeyCode.F11) { openMenu(); e.consume(); return; }
        if (code == KeyCode.F12) { rescan(); e.consume(); return; }

        var action = ProjectAction.byKey(code);
        if (action == null) return;
        e.consume();

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

    void onSearchKey(KeyEvent e) {
        switch (e.getCode()) {
            case ESCAPE -> { cancel(); e.consume(); }
            case ENTER -> { selectAndClose(); e.consume(); }
            // The arrows drive the table while the caret stays where it is. Focus follows, because
            // the table has to own the keys to scroll itself; typing a letter brings it straight
            // back, so from the user's side the caret never appears to have left.
            case DOWN -> { focusTable(); move(1); e.consume(); }
            case UP -> { focusTable(); move(-1); e.consume(); }
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
            case PRP -> openPrpManagerFor(rec);
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
        openPrpManagerFor(rec);
    }

    void openPrpManagerFor(ProjectRecord rec) {
        PrpManagerDialog.showOnFxThread(stage,
                littlejlib.proj.prp.PrpCreator.prpDirOf(rec.dir()), settings);
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
        var textMatch = ProjectFilter.textOnly(searchField.getText(), mavenOnly.isSelected());
        var parts = new ArrayList<String>();
        for (var type : CodeRepoBaseType.values()) {
            if (included.contains(type)) continue;
            var n = records.stream().filter(textMatch).filter(r -> typeOf.apply(r) == type).count();
            if (n > 0) parts.add(n + " in " + type.label);
        }
        return parts.isEmpty() ? "" : "   (" + String.join(", ", parts) + " - tick to include)";
    }

    /** Excludes the synthetic code-directory rows, which are scaffolding rather than results. */
    int visibleProjectCount() {
        var n = 0;
        for (var i = 0; i < table.getExpandedItemCount(); i++) {
            var item = table.getTreeItem(i);
            if (item != null && item.getValue() != null && !item.getValue().isCodeDirNode()) n++;
        }
        return n;
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
    void reopen(Object lock, Object[] holder) {
        result = null;
        stage.setOnHidden(e -> {
            synchronized (lock) {
                holder[0] = result;
                lock.notifyAll();
            }
        });
        searchField.clear();
        loadThenMaybeScan();
        stage.show();
        stage.toFront();
        stage.requestFocus();
        searchField.requestFocus();
    }

    /** Released only when the daemon exits; a hidden window keeps its watcher and its cache warm. */
    void dispose() {
        central.shutdown();
        if (watcher != null) watcher.stop();
    }

    public static PickerResult show(List<Path> browsePaths, Settings settings) {
        return PickerHost.show(browsePaths, settings);
    }
}
