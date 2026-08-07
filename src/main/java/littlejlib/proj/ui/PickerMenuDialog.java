package littlejlib.proj.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import littlejlib.proj.Settings;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.prp.PrpCreator;

/**
 * The picker's own menu: the things {@code proj} could do from its main menu that make sense while
 * a project is selected, without leaving the picker to get to them.
 * <p>
 * Deliberately not the whole of MainMenuDialog. New project, git clone and settings are driven by a
 * JLine terminal prompt, so they need a console this window does not have - offering them here would
 * produce a button that appears to do nothing. They stay on the command line, which is also where
 * they are now scriptable.
 * <p>
 * Numbered 1-8 for the eye only. The keys are F-keys and arrows, because a digit here would collide
 * with the filter box the moment this dialog closes and the picker gets its keystrokes back.
 */
public final class PickerMenuDialog {

    private PickerMenuDialog() { }

    public static void show(Stage owner, ProjectRecord selected, Settings settings,
                            Runnable onRescan, Runnable onAddBase, Runnable onAgent, Runnable onSettings) {

        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Menu");
        stage.setWidth(520);
        stage.setHeight(430);
        stage.setResizable(false);

        var hasProject = selected != null && !selected.isCodeDirNode();
        var prpDir = hasProject ? PrpCreator.prpDirOf(selected.dir()) : null;
        var target = hasProject ? selected.name() : "no project selected";

        var box = new VBox(8);
        box.setPadding(new Insets(18));

        var title = new Label("Menu");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #1976D2;");
        var subtitle = new Label("Selected: " + target);
        subtitle.setStyle("-fx-font-size: 11; -fx-text-fill: #777;");

        box.getChildren().addAll(title, subtitle, new Label(" "));

        box.getChildren().add(item("New PRP for this project", Palette.GREEN, hasProject,
                "Creates the next numbered PRP from the configured template and opens it",
                () -> { PrpManagerActions.createNewPrp(settings, prpDir); stage.close(); }));

        box.getChildren().add(item("PRP manager for this project", Palette.GREEN, hasProject,
                "Browse, open and close this project's PRPs   (F10 does this directly)",
                () -> { stage.close(); PrpManagerDialog.showOnFxThread(owner, prpDir, settings); }));

        box.getChildren().add(item("Global PRP viewer", Palette.TEAL, true,
                "Browse PRPs across every indexed project",
                () -> { stage.close(); GlobalPrpManagerDialog.showOnFxThread(owner, settings); }));

        box.getChildren().add(item("Dependencies of this project", Palette.TEAL, hasProject && selected.maven(),
                "What it depends on and what depends on it   (F9 does this directly)",
                stage::close));

        box.getChildren().add(item("Add a coderepobase", Palette.PINK, true,
                "Add a directory holding many repositories",
                () -> { stage.close(); onAddBase.run(); }));

        box.getChildren().add(item("Start a coding agent on a PRP", Palette.PURPLE, hasProject,
                "Opens your terminal at the PROJECT root - not the prp folder - running the agent "
                        + "with the PRP as its instruction",
                () -> { stage.close(); onAgent.run(); }));

        box.getChildren().add(item("Rescan everything", Palette.BLUE, true,
                "Re-walk every coderepobase   (F12 does this directly)",
                () -> { stage.close(); onRescan.run(); }));

        box.getChildren().add(item("Settings", Palette.GREY, true,
                "Code bases, terminals, editors, the agent command and scanning depth",
                () -> { stage.close(); onSettings.run(); }));

        var note = new Label("New project and git clone stay on the command line - "
                + "they prompt through the terminal, which this window does not have. "
                + "They are scriptable: proj new, proj clone.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 10; -fx-text-fill: #999; -fx-padding: 10 0 0 0;");
        box.getChildren().addAll(new Label(" "), note);

        box.setAlignment(Pos.TOP_LEFT);

        var scene = new Scene(box);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.F11) stage.close();
        });
        stage.setScene(scene);
        stage.showAndWait();
    }

    static Button item(String label, String style, boolean enabled, String tooltip, Runnable action) {
        var b = new Button(label);
        b.setPrefWidth(460);
        b.setPrefHeight(38);
        b.setStyle("-fx-font-size: 13; -fx-alignment: CENTER-LEFT; -fx-padding: 0 12 0 12;"
                + " -fx-background-radius: 3;" + style);
        b.setTooltip(new Tooltip(tooltip));
        b.setDisable(!enabled);
        b.setOnAction(e -> action.run());
        return b;
    }
}
