package littlejlib.proj.ui;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import littlejlib.proj.Settings;

/**
 * The settings window.
 * <p>
 * The controls are {@link SettingsPane}, which is also the main window's Settings tab. What is left
 * here is the window: a modal Stage, Enter to save and Esc to close. Settings were previously a
 * JLine menu in the terminal plus hand-editing the TOML, which is fine for a path and hopeless for
 * a list of command templates - the things most likely to need correcting are exactly the ones
 * hardest to type blind.
 */
public final class SettingsDialog {

    private SettingsDialog() { }

    /** Opened from the picker, which is already on the FX thread. True when the file was written. */
    public static boolean showOnFxThread(Stage owner, Settings settings) {
        var stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.setTitle("Settings");
        AppIcon.on(stage);
        var screen = Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1050, screen.getWidth() - 80));
        stage.setHeight(Math.min(720, screen.getHeight() - 80));

        var pane = new SettingsPane(settings, () -> stage, stage::close, stage::close);
        var scene = new Scene(pane.root);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);
        stage.showAndWait();
        return pane.wasSaved();
    }
}
