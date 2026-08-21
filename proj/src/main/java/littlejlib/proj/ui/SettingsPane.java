package littlejlib.proj.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import littlejlib.proj.*;
import littlejlib.proj.prp.AgentLauncher;
import littlejlib.proj.terminal.TerminalDetector;
import littlejlib.proj.terminal.TerminalRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The settings themselves, as a NODE - so the same controls are the body of the settings window and
 * the Settings tab of the main window, rather than two editors of one file that can disagree.
 * <p>
 * Two principles run through it, both older than this class. Detected entries are OFFERED, never
 * silently written: the Detect buttons add rows you can then edit, so what the file says is always
 * what you agreed to. And every command template is editable text rather than a fixed dropdown,
 * because the detector will eventually meet a terminal or an IDE it does not know, and a settings
 * window that cannot express that is a settings window that sends you back to the TOML.
 */
public final class SettingsPane {

    public final VBox root = new VBox(10);
    final Settings settings;
    final Supplier<Window> owner;
    boolean saved;

    final javafx.collections.ObservableList<CodeRepoBase> bases;
    final javafx.collections.ObservableList<IdeLauncher> ides;
    final javafx.collections.ObservableList<TerminalLauncher> terminals;
    final Spinner<Integer> depth, logsTail;
    final TextField editor, prpTemplate, agentCommand;
    final CheckBox skipPermissions = new CheckBox("Run the agent with no permission prompts");
    final ComboBox<String> defaultTerminal = new ComboBox<>();
    // Editable, not a fixed list: the presets are the ones worth suggesting, and any combination
    // RegisterHotKey accepts is legitimate. A dropdown that cannot express ctrl+alt+p is a dropdown
    // that sends you back to the TOML.
    final ComboBox<String> summonKey = new ComboBox<>();
    final java.util.Map<PickerColumn, CheckBox> columnBoxes = new java.util.LinkedHashMap<>();

    /**
     * @param onClose null in tab mode, where there is no window to close and therefore no Cancel -
     *                a tab you can leave by clicking another tab does not need one, and a Cancel
     *                that only reverted the fields would be a third meaning for the same word
     */
    public SettingsPane(Settings settings, Supplier<Window> owner, Runnable afterSave, Runnable onClose) {
        this.settings = settings;
        this.owner = owner;

        bases = observable(settings.getCodeRepoBases());
        ides = observable(settings.getIdeLaunchers());
        terminals = observable(settings.getTerminalLaunchers().isEmpty()
                ? TerminalDetector.detect() : settings.getTerminalLaunchers());

        depth = new Spinner<>(1, 12, settings.getProjectScanningDepth());
        depth.setEditable(true);
        depth.setPrefWidth(90);

        editor = new TextField(nz(settings.getSingleFileEditor()));
        editor.setPromptText("Editor for single files - leave blank to use whatever is installed");

        prpTemplate = new TextField(PRPTemplateLoader.effectiveSrc(settings.getPrpTemplateSrc()));
        prpTemplate.setPromptText("bundled, or a URL, or a local path");

        // templateOf, not the raw setting: a file still carrying the OLD default is showing a value
        // nobody chose, and putting it in the box would make it look chosen.
        agentCommand = new TextField(AgentLauncher.templateOf(settings));

        skipPermissions.setSelected(settings.isAgentSkipPermissions());
        logsTail = new Spinner<>(0, 100, settings.getAgentLogsTailPercent());
        logsTail.setEditable(true);
        logsTail.setPrefWidth(90);

        defaultTerminal.setEditable(true);
        defaultTerminal.setPrefWidth(220);

        summonKey.setEditable(true);
        summonKey.setPrefWidth(200);
        summonKey.getItems().setAll("win+\\", "win+`", "win+f12", "ctrl+alt+p",
                "scrolllock", "pause", "menu", "off");
        summonKey.setValue(nz(settings.getGlobalHotkey()));

        // One tick per picker column, in the order the picker will show them. An empty hidden list
        // means "nobody has chosen yet", not "everything is on", so the boxes start from each
        // column's own default until a layout has actually been recorded.
        var hiddenNow = Set.copyOf(settings.getPickerHiddenColumns());
        var chosenBefore = !settings.getPickerColumnOrder().isEmpty() || !hiddenNow.isEmpty();
        for (var which : PickerColumn.order(settings.getPickerColumnOrder())) {
            var box = new CheckBox(which.title);
            box.setSelected(chosenBefore ? !hiddenNow.contains(which.id) : which.shownByDefault);
            box.setTooltip(new Tooltip("id: " + which.id));
            columnBoxes.put(which, box);
        }

        var tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        tabs.getTabs().add(new Tab("Code bases", basesTab()));
        tabs.getTabs().add(new Tab("Terminals", terminalsTab()));
        tabs.getTabs().add(new Tab("Editors and IDEs", idesTab()));
        tabs.getTabs().add(new Tab("PRP and agent", prpTab()));
        tabs.getTabs().add(new Tab("Picker columns", columnsTab()));
        tabs.getTabs().add(new Tab("General", generalTab()));

        var file = new Label("Settings file: " + new SettingsManager().getSettingsFilePath());
        file.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

        // Enter and Esc rather than a mnemonic. The old "_Save" put the whole dialog one stray S
        // away from writing the settings file - every tab here has a text field, and a JavaFX
        // mnemonic stops asking for Alt the moment anything in the window has been Alt-pressed once.
        var save = new Button("Save");
        save.setTooltip(new Tooltip("Write the settings file" + (onClose == null ? "" : "   (Enter)")));
        save.setDefaultButton(onClose != null);
        save.setOnAction(e -> {
            apply();
            saved = true;
            if (afterSave != null) afterSave.run();
        });

        var spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var buttons = new HBox(8, file, spacer);
        if (onClose != null) {
            var cancel = new Button("Cancel");
            cancel.setTooltip(new Tooltip("Close without saving   (Esc)"));
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> onClose.run());
            buttons.getChildren().add(cancel);
        }
        buttons.getChildren().add(save);
        buttons.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(tabs, buttons);
        root.setPadding(new Insets(12));
        Keys.noMnemonics(root);

        // The names go in after the terminals list exists, so the dropdown offers what is actually
        // there rather than a fixed list that goes stale the moment one is added.
        refreshTerminalNames(settings.getDefaultTerminal());
        terminals.addListener((javafx.collections.ListChangeListener<TerminalLauncher>)
                c -> refreshTerminalNames(defaultTerminal.getValue()));
    }

    public boolean wasSaved() {
        return saved;
    }

    void apply() {
        settings.setCodeRepoBases(new ArrayList<>(bases));
        settings.setIdeLaunchers(new ArrayList<>(ides));
        settings.setTerminalLaunchers(new ArrayList<>(terminals));
        settings.setDefaultTerminal(defaultTerminal.getValue());
        settings.setAgentCommand(agentCommand.getText());
        settings.setAgentSkipPermissions(skipPermissions.isSelected());
        settings.setAgentLogsTailPercent(logsTail.getValue());
        settings.setSingleFileEditor(editor.getText());
        settings.setPrpTemplateSrc(prpTemplate.getText());
        settings.setProjectScanningDepth(depth.getValue());
        settings.setGlobalHotkey(nz(summonKey.getValue()).isBlank()
                ? littlejlib.proj.util.GlobalHotkey.DEFAULT : summonKey.getValue().trim());

        var hiddenColumns = new ArrayList<String>();
        columnBoxes.forEach((which, box) -> {
            if (!box.isSelected()) hiddenColumns.add(which.id);
        });
        settings.setPickerHiddenColumns(hiddenColumns);
        // Record the order too, even untouched. Without it the hidden list stays ambiguous - an
        // empty one would keep reading as "never chosen", and ticking every box back on would
        // silently restore the defaults instead of showing everything.
        if (settings.getPickerColumnOrder().isEmpty()) {
            settings.setPickerColumnOrder(columnBoxes.keySet().stream().map(c -> c.id).toList());
        }

        new SettingsManager().saveSettings(settings);
        // The picker builds its columns once, at construction, and the daemon keeps that window for
        // days. Without this the ticks above would not take effect until the daemon restarted.
        littlejlib.proj.ide.IdeDetector.forget();
        TerminalDetector.forget();
        PickerHost.invalidate();
    }

    void refreshTerminalNames(String keep) {
        var names = new ArrayList<String>();
        names.add("");
        terminals.stream().map(TerminalLauncher::getName).filter(n -> n != null && !n.isBlank())
                .forEach(names::add);
        defaultTerminal.getItems().setAll(names);
        defaultTerminal.setValue(keep == null ? "" : keep);
    }

    // ---------- tabs ----------

    VBox basesTab() {
        var table = new TableView<>(bases);
        table.setEditable(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPlaceholder(new Label("No coderepobase configured - add the directory holding your repositories"));

        var path = new TableColumn<CodeRepoBase, String>("Directory");
        path.setPrefWidth(560);
        path.setCellValueFactory(p -> new SimpleStringProperty(nz(p.getValue().getPath())));
        table.getColumns().add(path);

        var type = new TableColumn<CodeRepoBase, String>("Type");
        type.setPrefWidth(140);
        type.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getType().label));
        table.getColumns().add(type);

        var add = new Button("Add...");
        add.setOnAction(e -> {
            var chooser = new DirectoryChooser();
            chooser.setTitle("Choose a coderepobase - a directory holding many repositories");
            var dir = chooser.showDialog(owner.get());
            if (dir == null) return;
            var choice = new ChoiceDialog<>(CodeRepoBaseType.PRIMARY, CodeRepoBaseType.values());
            choice.setTitle("Add coderepobase");
            choice.setHeaderText(dir.getAbsolutePath());
            choice.setContentText("What kind is it?");
            AppIcon.on(choice);
            choice.showAndWait().ifPresent(t -> bases.add(new CodeRepoBase(dir.getAbsolutePath(), t)));
        });

        var retype = new Button("Change type");
        retype.setOnAction(e -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            var choice = new ChoiceDialog<>(selected.getType(), CodeRepoBaseType.values());
            choice.setTitle("Change type");
            choice.setHeaderText(selected.getPath());
            choice.setContentText("What kind is it?");
            AppIcon.on(choice);
            choice.showAndWait().ifPresent(t -> {
                selected.setType(t);
                table.refresh();
            });
        });

        return withControls(table,
                "Only PRIMARY shows by default. Mirror and auxiliary stay indexed and searchable, "
                        + "behind their checkboxes in the picker.",
                add, retype, removeButton(table, bases));
    }

    VBox terminalsTab() {
        var table = editableTable(terminals, List.of(
                column("Name", 150, TerminalLauncher::getName, TerminalLauncher::setName),
                column("Open a shell here (%PATH%)", 360,
                        TerminalLauncher::getOpenCommand, TerminalLauncher::setOpenCommand),
                column("Run a command here (%SCRIPT%)", 400,
                        TerminalLauncher::getRunCommand, TerminalLauncher::setRunCommand)),
                "No terminal registered - press Detect, or add one by hand");

        var detect = new Button("Detect installed");
        detect.setOnAction(e -> {
            TerminalDetector.forget();
            var existing = terminals.stream().map(t -> nz(t.getName()).toLowerCase()).toList();
            var added = 0;
            for (var t : TerminalDetector.detect()) {
                if (existing.contains(nz(t.getName()).toLowerCase())) continue;
                terminals.add(t);
                added++;
            }
            Toast.show(added == 0 ? "nothing new found" : "added " + added);
        });

        var add = new Button("Add blank");
        add.setOnAction(e -> terminals.add(new TerminalLauncher("New terminal", "", "")));

        var test = new Button("Test selected");
        test.setOnAction(e -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || !selected.isUsable()) {
                Toast.show("select a terminal with an open command first");
                return;
            }
            var probe = new Settings();
            probe.setTerminalLaunchers(List.of(selected));
            probe.setDefaultTerminal(selected.getName());
            TerminalRunner.open(java.nio.file.Path.of(System.getProperty("user.home")), probe);
        });

        var defaultRow = new HBox(8, new Label("Use by default"), defaultTerminal);
        defaultRow.setAlignment(Pos.CENTER_LEFT);

        var box = withControls(table,
                "%PATH% is the directory. %SCRIPT% is a generated script that changes into that "
                        + "directory and then runs the command - it exists because several terminals "
                        + "have no way to say \"run this, over there\" in one go. A terminal with an "
                        + "empty run template can still open a shell; it simply will not be offered "
                        + "for starting an agent.",
                detect, add, test, removeButton(table, terminals));
        box.getChildren().add(defaultRow);
        return box;
    }

    VBox idesTab() {
        var table = editableTable(ides, List.of(
                column("Name", 180, IdeLauncher::getName, IdeLauncher::setName),
                column("Command (%PATH% is the project directory)", 700,
                        IdeLauncher::getCommand, IdeLauncher::setCommand)),
                "None configured - the picker will offer whatever it detects");

        var detect = new Button("Detect installed");
        detect.setOnAction(e -> {
            littlejlib.proj.ide.IdeDetector.forget();
            var existing = ides.stream().map(i -> nz(i.getName()).toLowerCase()).toList();
            var added = 0;
            for (var d : littlejlib.proj.ide.IdeDetector.detect()) {
                if (!d.opensFolders || existing.contains(d.name.toLowerCase())) continue;
                ides.add(new IdeLauncher(d.name, d.command()));
                added++;
            }
            Toast.show(added == 0 ? "nothing new found" : "added " + added);
        });

        var add = new Button("Add blank");
        add.setOnAction(e -> ides.add(new IdeLauncher("New IDE", "")));

        var box = withControls(table,
                "Leave this empty to let the picker offer whatever is installed. Configure entries "
                        + "here only to override that - a configured entry wins over the DETECTED "
                        + "copy of the same executable, not over the whole list.",
                detect, add, removeButton(table, ides));

        var editorRow = new HBox(8, new Label("Single-file editor"), editor);
        HBox.setHgrow(editor, Priority.ALWAYS);
        editorRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(editorRow);
        return box;
    }

    VBox prpTab() {
        var grid = new VBox(8);
        grid.setPadding(new Insets(10));

        grid.getChildren().add(new Label("PRP template source"));
        grid.getChildren().add(prpTemplate);

        var templateNote = new Label("'bundled' is the template built into proj - no network, and "
                + "nothing to go stale. Put an http(s) URL or a local file path here instead to "
                + "override it, which is how a team shares one template; a URL is cached after the "
                + "first fetch, and falls back to the bundled one if it cannot be reached.");
        templateNote.setWrapText(true);
        templateNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        grid.getChildren().add(templateNote);

        var useBundled = new Button("Use the bundled template");
        useBundled.setOnAction(e -> prpTemplate.setText(PRPTemplateLoader.DEFAULT_TEMPLATE_SRC));
        grid.getChildren().add(useBundled);

        var note = new Label("Starting a coding agent on a PRP. The working directory is always the "
                + "PROJECT ROOT, never the prp folder - an agent started one level too deep sees a "
                + "folder of markdown and none of the code.\n\n"
                + "%PROMPT%   the instruction, assembled from what is actually on disk: the PRP, its "
                + "status file, and the tail of the last session's log. Each part is included only "
                + "if that file exists.\n"
                + "%FLAGS%    the permission flag below, or nothing\n"
                + "%PRP%      the PRP path relative to the project, e.g. prp/14-prp-name.md\n"
                + "%PRP_ABS%  its absolute path\n"
                + "%PATH%     the project directory");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        grid.getChildren().add(new Separator());
        grid.getChildren().add(new Label("Agent command"));
        grid.getChildren().add(agentCommand);
        grid.getChildren().add(note);

        var reset = new Button("Reset to default");
        reset.setOnAction(e -> agentCommand.setText(AgentLauncher.DEFAULT_AGENT_COMMAND));
        grid.getChildren().add(reset);

        grid.getChildren().add(new Separator());

        var tailRow = new HBox(8, new Label("Read this much of the last session's log"), logsTail,
                new Label("% from the end"));
        tailRow.setAlignment(Pos.CENTER_LEFT);
        grid.getChildren().add(tailRow);

        var tailNote = new Label("A PRP worked on before has exported logs beside it "
                + "(NN-prp.99.SS.logs.txt). The agent is pointed at the newest one so a fresh "
                + "session starts knowing how the last one ended. 0 leaves it out; nothing is said "
                + "about a log that does not exist.");
        tailNote.setWrapText(true);
        tailNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        grid.getChildren().add(tailNote);

        grid.getChildren().add(new Separator());
        skipPermissions.setStyle("-fx-font-weight: bold;");
        grid.getChildren().add(skipPermissions);

        var dangerNote = new Label("Adds " + littlejlib.proj.prp.PrpPrompt.SKIP_PERMISSIONS
                + " to every agent started from here. The agent then edits files, runs commands and "
                + "installs things without asking you first - which is the point when you are "
                + "working this way all day, and is not something to leave on by accident. Off "
                + "unless you tick it; the launch window says which state you are in.");
        dangerNote.setWrapText(true);
        dangerNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #E65100;");
        grid.getChildren().add(dangerNote);
        return grid;
    }

    /**
     * Which columns the picker shows. Order is not edited here on purpose: dragging a header is a
     * better way to say it than a pair of up/down buttons, and the picker already writes that back.
     */
    VBox columnsTab() {
        var grid = new VBox(6);
        grid.setPadding(new Insets(10));
        columnBoxes.values().forEach(grid.getChildren()::add);

        var all = new Button("Show all");
        all.setOnAction(e -> columnBoxes.values().forEach(b -> b.setSelected(true)));
        var defaults = new Button("Reset to defaults");
        defaults.setOnAction(e -> columnBoxes.forEach((which, b) -> b.setSelected(which.shownByDefault)));

        var bar = new HBox(8, all, defaults);
        bar.setAlignment(Pos.CENTER_LEFT);

        var note = new Label("The same ticks are in the picker's own menu button, at the top right of "
                + "its header - this tab exists so they can be found. Column ORDER is not set here: "
                + "drag a header in the picker instead, which is remembered the same way.\n\n"
                + "JavaFX cannot freeze a column, so on a narrow screen fewer columns is the only "
                + "real cure for having to scroll sideways to read a row.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        var box = new VBox(10, grid, bar, note);
        box.setPadding(new Insets(10));
        return box;
    }

    VBox generalTab() {
        var box = new VBox(8);
        box.setPadding(new Insets(10));

        var summonRow = new HBox(8, new Label("Summon this window from anywhere"), summonKey);
        summonRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(summonRow);

        var summonNote = new Label("Windows is asked to CLAIM this key, so nothing else sees it and "
                + "nothing fires twice. The consequence worth knowing: a combination something else "
                + "already owns cannot be claimed, and the daemon log says so at startup rather than "
                + "leaving you with a key that does nothing.\n\n"
                + "A bare key with no modifier is allowed, and is the kindest on the hands - "
                + "claiming scrolllock, pause or menu also suppresses whatever they would otherwise "
                + "have done. Anything else is modifiers plus one key: win, ctrl, alt, shift, joined "
                + "with +.\n\n"
                + "It needs the daemon running, and takes effect when the daemon restarts: "
                + "proj daemon restart.");
        summonNote.setWrapText(true);
        summonNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        box.getChildren().add(summonNote);

        box.getChildren().add(new Separator());

        var row = new HBox(8, new Label("Project scanning depth"), depth);
        row.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(row);
        var note = new Label("How many directory levels below a coderepobase the scanner descends. "
                + "A maven project with its own src/ ends the walk regardless, so this mainly governs "
                + "how deeply nested a repository may be before it stops being found.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        box.getChildren().add(note);
        return box;
    }

    // ---------- plumbing ----------

    record Col<T>(String title, double width, Function<T, String> get,
                  java.util.function.BiConsumer<T, String> set) { }

    static <T> Col<T> column(String title, double width, Function<T, String> get,
                             java.util.function.BiConsumer<T, String> set) {
        return new Col<>(title, width, get, set);
    }

    static <T> TableView<T> editableTable(javafx.collections.ObservableList<T> items,
                                          List<Col<T>> columns, String placeholder) {
        var table = new TableView<>(items);
        table.setEditable(true);
        table.setPlaceholder(new Label(placeholder));
        VBox.setVgrow(table, Priority.ALWAYS);

        for (var c : columns) {
            var col = new TableColumn<T, String>(c.title());
            col.setPrefWidth(c.width());
            col.setCellValueFactory(p -> new SimpleStringProperty(nz(c.get().apply(p.getValue()))));
            col.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
            col.setOnEditCommit(e -> c.set().accept(e.getRowValue(), e.getNewValue()));
            table.getColumns().add(col);
        }
        return table;
    }

    static <T> Button removeButton(TableView<T> table, javafx.collections.ObservableList<T> items) {
        var remove = new Button("Remove");
        remove.setOnAction(e -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) items.remove(selected);
        });
        return remove;
    }

    static VBox withControls(TableView<?> table, String help, Button... buttons) {
        var bar = new HBox(8);
        bar.getChildren().addAll(buttons);
        bar.setAlignment(Pos.CENTER_LEFT);

        var note = new Label(help);
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        var box = new VBox(8, table, bar, note);
        box.setPadding(new Insets(10));
        return box;
    }

    static <T> javafx.collections.ObservableList<T> observable(List<T> source) {
        return javafx.collections.FXCollections.observableArrayList(new ArrayList<>(source));
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
