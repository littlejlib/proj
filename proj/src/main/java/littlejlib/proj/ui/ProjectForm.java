package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import littlejlib.proj.prp.*;

import java.nio.file.Path;

/** The fields of {@link ProjectDialog}. Enter saves, Esc cancels, F4 picks an icon file. */
final class ProjectForm {

    final Path project;
    final ProjJson current;
    final TextField name = new TextField(), longName = new TextField(), icon = new TextField();
    final ImageView preview = new ImageView();
    final Label ticket = new Label(), clash = new Label();
    final String example;

    ProjectForm(Path project) {
        this.project = project;
        this.current = ProjFile.ensure(project);
        this.example = exampleIndex(project);
        name.setText(current.name());
        longName.setText(current.longName() == null ? "" : current.longName());
        icon.setText(current.icon() == null ? "" : current.icon());
    }

    /** A real PRP of this project, so the preview shows a number the user recognises. */
    static String exampleIndex(Path project) {
        try {
            return PrpCreator.list(PrpCreator.prpDirOf(project)).stream().map(PrpEntry::getIndex)
                    .max(String::compareTo).orElse("38");
        } catch (Exception e) {
            return "38";
        }
    }

    Dialog<ButtonType> dialog() {
        var dialog = new Dialog<ButtonType>();
        AppIcon.on(dialog);
        dialog.setTitle("Project");
        dialog.setHeaderText(project.toString());
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content());
        Keys.noMnemonics(dialog.getDialogPane());
        dialog.setOnShown(e -> Platform.runLater(() -> {
            name.requestFocus();
            name.selectAll();
        }));
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F4) {
                choose();
                e.consume();
            }
        });
        return dialog;
    }

    Node content() {
        var choose = new Button("F4 Choose file");
        choose.setOnAction(e -> choose());
        preview.setFitWidth(32);
        preview.setFitHeight(32);
        name.setPrefColumnCount(18);
        longName.setPrefColumnCount(34);
        icon.setPrefColumnCount(34);
        icon.setPromptText("empty: the GitHub organisation's icon, if the folder is a clone");

        var grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 0, 0, 0));
        var nameRow = new HBox(10, name, clash);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        grid.addRow(0, new Label("Short name"), nameRow);
        grid.addRow(1, new Label("Long name"), longName);
        grid.addRow(2, new Label("Icon"), new HBox(8, icon, choose, preview));
        grid.addRow(3, new Label("uuid"), readOnly(current.uuid()));

        ticket.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
        clash.setStyle("-fx-text-fill: #C62828; -fx-font-size: 11;");
        var hints = new Label("Enter saves   -   Esc cancels   -   F4 picks an icon file");
        hints.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
        name.textProperty().addListener((o, a, b) -> refreshTicket());
        icon.textProperty().addListener((o, a, b) -> refreshPreview());
        refreshTicket();
        refreshPreview();

        var box = new VBox(10, grid, ticket, hints);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    static TextField readOnly(String text) {
        var f = new TextField(text);
        f.setEditable(false);
        f.setPrefColumnCount(34);
        return f;
    }

    void refreshTicket() {
        var key = TabName.cleanKey(name.getText());
        ticket.setText(key.isEmpty() ? "" : "PRP " + example + " reads " + key + "-" + example
                + ", and its terminal tab " + key + "-" + example + "-<task>");
        var owner = key.isEmpty() ? null : PrpSessions.keyOwner(key, project);
        clash.setText(owner == null ? "" : "also used by " + owner);
    }

    void refreshPreview() {
        var typed = icon.getText().strip();
        var shown = typed.isEmpty() || typed.startsWith("http")
                ? ProjIcon.current(project, current) : project.resolve(typed);
        preview.setImage(shown != null && shown.toFile().isFile()
                ? new Image(shown.toUri().toString(), 32, 32, true, true) : null);
    }

    void choose() {
        var chooser = new FileChooser();
        chooser.setTitle("Icon for " + project.getFileName());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        var file = chooser.showOpenDialog(icon.getScene().getWindow());
        if (file == null) return;
        var picked = file.toPath();
        icon.setText(picked.startsWith(project) ? project.relativize(picked).toString() : picked.toString());
    }

    void save(Runnable afterChange) {
        var key = TabName.cleanKey(name.getText());
        if (key.isEmpty()) {
            Toast.show("a short name needs at least one letter or digit");
            return;
        }
        var typedIcon = icon.getText().strip();
        var iconChanged = !typedIcon.equals(current.icon() == null ? "" : current.icon());
        ProjFile.update(project, p -> {
            p.name(key);
            p.longName(longName.getText().isBlank() ? null : longName.getText().strip());
            p.icon(typedIcon.isEmpty() ? null : typedIcon);
        });
        Toast.show("PRPs of this project now read " + key + "-NN");
        if (afterChange != null) afterChange.run();
        if (iconChanged) refreshIconInBackground();
    }

    void refreshIconInBackground() {
        var t = new Thread(() -> ProjIcon.refresh(project, ProjFile.ensure(project), true), "proj-icon");
        t.setDaemon(true);
        t.start();
    }
}
