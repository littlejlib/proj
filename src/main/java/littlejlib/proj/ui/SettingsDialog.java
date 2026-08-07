package littlejlib.proj.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import littlejlib.proj.*;
import littlejlib.proj.prp.AgentLauncher;
import littlejlib.proj.terminal.TerminalDetector;
import littlejlib.proj.terminal.TerminalRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The settings window.
 * <p>
 * Settings were previously a JLine menu in the terminal plus hand-editing Settings.xml, which is
 * fine for a path and hopeless for a list of command templates - the things most likely to need
 * correcting are exactly the ones hardest to type blind into XML.
 * <p>
 * Two principles run through it. Detected entries are OFFERED, never silently written: the Detect
 * buttons add rows you can then edit, so what the file says is always what you agreed to. And every
 * command template is editable text rather than a fixed dropdown, because the detector will
 * eventually meet a terminal or an IDE it does not know, and a settings window that cannot express
 * that is a settings window that sends you back to the XML.
 */
public final class SettingsDialog {

    private SettingsDialog() { }

    /** Opened from the picker, which is already on the FX thread. */
    public static boolean showOnFxThread(Stage owner, Settings settings) {
        var stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.setTitle("Settings");
        stage.setWidth(Math.min(1050, javafx.stage.Screen.getPrimary().getVisualBounds().getWidth() - 80));
        stage.setHeight(Math.min(720, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 80));

        var saved = new boolean[]{false};

        var bases = FXCollections(settings.getCodeRepoBases());
        var ides = FXCollections(settings.getIdeLaunchers());
        var terminals = FXCollections(settings.getTerminalLaunchers().isEmpty()
                ? TerminalDetector.detect() : settings.getTerminalLaunchers());

        var depth = new Spinner<Integer>(1, 12, settings.getProjectScanningDepth());
        depth.setEditable(true);
        depth.setPrefWidth(90);

        var editor = new TextField(nz(settings.getSingleFileEditor()));
        editor.setPromptText("Editor for single files - leave blank to use whatever is installed");

        var prpTemplate = new TextField(nz(settings.getPrpTemplateSrc()));
        prpTemplate.setPromptText("URL or local path to the PRP template");

        var defaultTerminal = new ComboBox<String>();
        defaultTerminal.setEditable(true);
        defaultTerminal.setPrefWidth(220);

        var agentCommand = new TextField(settings.getAgentCommand() == null
                ? AgentLauncher.DEFAULT_AGENT_COMMAND : settings.getAgentCommand());

        // One tick per picker column, in the order the picker will show them. An empty hidden list
        // means "nobody has chosen yet", not "everything is on", so the boxes start from each
        // column's own default until a layout has actually been recorded.
        var hiddenNow = Set.copyOf(settings.getPickerHiddenColumns());
        var chosenBefore = !settings.getPickerColumnOrder().isEmpty() || !hiddenNow.isEmpty();
        var columnBoxes = new java.util.LinkedHashMap<PickerColumn, CheckBox>();
        for (var which : PickerColumn.order(settings.getPickerColumnOrder())) {
            var box = new CheckBox(which.title);
            box.setSelected(chosenBefore ? !hiddenNow.contains(which.id) : which.shownByDefault);
            box.setTooltip(new Tooltip("id: " + which.id));
            columnBoxes.put(which, box);
        }

        var tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        tabs.getTabs().add(new Tab("Code bases", basesTab(stage, bases)));
        tabs.getTabs().add(new Tab("Terminals", terminalsTab(terminals, defaultTerminal)));
        tabs.getTabs().add(new Tab("Editors and IDEs", idesTab(ides, editor)));
        tabs.getTabs().add(new Tab("PRP and agent", prpTab(prpTemplate, agentCommand)));
        tabs.getTabs().add(new Tab("Picker columns", columnsTab(columnBoxes)));
        tabs.getTabs().add(new Tab("Scanning", scanningTab(depth)));

        var file = new Label("Settings file: " + new SettingsManager().getSettingsFilePath());
        file.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

        var save = new Button("_Save");
        save.setMnemonicParsing(true);
        save.setDefaultButton(true);
        var cancel = new Button("Cancel");
        cancel.setCancelButton(true);

        save.setOnAction(e -> {
            settings.setCodeRepoBases(new ArrayList<>(bases));
            settings.setIdeLaunchers(new ArrayList<>(ides));
            settings.setTerminalLaunchers(new ArrayList<>(terminals));
            settings.setDefaultTerminal(defaultTerminal.getValue());
            settings.setAgentCommand(agentCommand.getText());
            settings.setSingleFileEditor(editor.getText());
            settings.setPrpTemplateSrc(prpTemplate.getText());
            settings.setProjectScanningDepth(depth.getValue());

            var hiddenColumns = new ArrayList<String>();
            columnBoxes.forEach((which, box) -> {
                if (!box.isSelected()) hiddenColumns.add(which.id);
            });
            settings.setPickerHiddenColumns(hiddenColumns);
            // Record the order too, even untouched. Without it the hidden list stays ambiguous -
            // an empty one would keep reading as "never chosen", and ticking every box back on
            // would silently restore the defaults instead of showing everything.
            if (settings.getPickerColumnOrder().isEmpty()) {
                settings.setPickerColumnOrder(columnBoxes.keySet().stream().map(c -> c.id).toList());
            }

            new SettingsManager().saveSettings(settings);
            // The picker builds its columns once, at construction, and the daemon keeps that window
            // for days. Without this the ticks above would not take effect until the daemon restarted.
            PickerHost.invalidate();
            saved[0] = true;
            stage.close();
        });
        cancel.setOnAction(e -> stage.close());

        var spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var buttons = new HBox(8, file, spacer, cancel, save);
        buttons.setAlignment(Pos.CENTER_LEFT);

        var root = new VBox(10, tabs, buttons);
        root.setPadding(new Insets(12));

        // The names go in after the terminals list exists, so the dropdown offers what is actually
        // there rather than a fixed list that goes stale the moment one is added.
        refreshTerminalNames(defaultTerminal, terminals, settings.getDefaultTerminal());
        terminals.addListener((javafx.collections.ListChangeListener<TerminalLauncher>)
                c -> refreshTerminalNames(defaultTerminal, terminals, defaultTerminal.getValue()));

        var scene = new Scene(root);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);
        stage.showAndWait();
        return saved[0];
    }

    static void refreshTerminalNames(ComboBox<String> box, List<TerminalLauncher> terminals, String keep) {
        var names = new ArrayList<String>();
        names.add("");
        terminals.stream().map(TerminalLauncher::getName).filter(n -> n != null && !n.isBlank())
                .forEach(names::add);
        box.getItems().setAll(names);
        box.setValue(keep == null ? "" : keep);
    }

    // ---------- tabs ----------

    static VBox basesTab(Stage owner, javafx.collections.ObservableList<CodeRepoBase> bases) {
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
            var dir = chooser.showDialog(owner);
            if (dir == null) return;
            var choice = new ChoiceDialog<>(CodeRepoBaseType.PRIMARY, CodeRepoBaseType.values());
            choice.setTitle("Add coderepobase");
            choice.setHeaderText(dir.getAbsolutePath());
            choice.setContentText("What kind is it?");
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
            choice.showAndWait().ifPresent(t -> {
                selected.setType(t);
                table.refresh();
            });
        });

        var remove = removeButton(table, bases);

        return withControls(table,
                "Only PRIMARY shows by default. Mirror and auxiliary stay indexed and searchable, "
                        + "behind their checkboxes in the picker.",
                add, retype, remove);
    }

    static VBox terminalsTab(javafx.collections.ObservableList<TerminalLauncher> terminals,
                             ComboBox<String> defaultTerminal) {
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

        var remove = removeButton(table, terminals);

        var defaultRow = new HBox(8, new Label("Use by default"), defaultTerminal);
        defaultRow.setAlignment(Pos.CENTER_LEFT);

        var box = withControls(table,
                "%PATH% is the directory. %SCRIPT% is a generated script that changes into that "
                        + "directory and then runs the command - it exists because several terminals "
                        + "(Tabby among them) have no way to say \"run this, over there\" in one go.",
                detect, add, test, remove);
        box.getChildren().add(defaultRow);
        return box;
    }

    static VBox idesTab(javafx.collections.ObservableList<IdeLauncher> ides, TextField editor) {
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
                        + "here only to override that - a configured list wins outright.",
                detect, add, removeButton(table, ides));

        var editorRow = new HBox(8, new Label("Single-file editor"), editor);
        HBox.setHgrow(editor, Priority.ALWAYS);
        editorRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(editorRow);
        return box;
    }

    static VBox prpTab(TextField prpTemplate, TextField agentCommand) {
        var grid = new VBox(8);
        grid.setPadding(new Insets(10));

        grid.getChildren().add(new Label("PRP template source"));
        grid.getChildren().add(prpTemplate);

        var note = new Label("Starting a coding agent on a PRP. The working directory is always the "
                + "PROJECT ROOT, never the prp folder - an agent started one level too deep sees a "
                + "folder of markdown and none of the code.\n\n"
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
        return grid;
    }

    /**
     * Which columns the picker shows. Order is not edited here on purpose: dragging a header is a
     * better way to say it than a pair of up/down buttons, and the picker already writes that back.
     */
    static VBox columnsTab(java.util.Map<PickerColumn, CheckBox> boxes) {
        var grid = new VBox(6);
        grid.setPadding(new Insets(10));
        boxes.values().forEach(grid.getChildren()::add);

        var all = new Button("Show all");
        all.setOnAction(e -> boxes.values().forEach(b -> b.setSelected(true)));
        var defaults = new Button("Reset to defaults");
        defaults.setOnAction(e -> boxes.forEach((which, b) -> b.setSelected(which.shownByDefault)));

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

    static VBox scanningTab(Spinner<Integer> depth) {
        var box = new VBox(8);
        box.setPadding(new Insets(10));
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

    record Col<T>(String title, double width, Function<T, String> get, java.util.function.BiConsumer<T, String> set) { }

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

    static <T> javafx.collections.ObservableList<T> FXCollections(List<T> source) {
        return javafx.collections.FXCollections.observableArrayList(new ArrayList<>(source));
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
