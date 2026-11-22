package littlejlib.proj.ui;

import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import littlejlib.proj.Settings;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The per-project PRP manager as a window of its own.
 * <p>
 * Everything it used to be is now {@link PrpPanel}, which is also the side panel beside the project
 * list and the body of the global tab. What is left here is what a WINDOW has to do and a panel does
 * not: exist as a Stage, block the daemon's caller until a person closes it, and forward the
 * keyboard to the panel - which in a window with nothing else in it is every key, unconditionally.
 * <p>
 * The window is kept because the picker is not the only way in: {@code proj} opened from a project
 * directory goes straight here through the menu, with no project list to sit beside.
 */
public final class PrpManagerDialog {

    private PrpManagerDialog() { }

    /**
     * From a non-UI thread - the daemon. Long timeout because what is being waited on is a person;
     * a short one would answer the client while the window was still open in front of them.
     */
    public static void show(Path prpDir, Settings settings) {
        JavaFXManager.getInstance().showDialog(
                (lock, holder) -> {
                    var stage = build(prpDir, settings);
                    stage.setOnHidden(e -> {
                        synchronized (lock) {
                            holder[0] = "CLOSED";
                            lock.notifyAll();
                        }
                    });
                    stage.show();
                },
                900_000);
    }

    /**
     * From code already on the JavaFX thread - the picker.
     * <p>
     * {@link #show} cannot be used from there: it blocks the calling thread on a lock and schedules
     * the build through {@code Platform.runLater}, so from the FX thread the scheduled work can
     * never run - the thread that would run it is the one waiting.
     */
    public static void showOnFxThread(Stage owner, Path prpDir, Settings settings) {
        var stage = build(prpDir, settings);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.showAndWait();
    }

    static Stage build(Path prpDir, Settings settings) {
        var stage = new Stage();
        stage.setTitle("PRP Manager - Project Requirement Prompts");
        AppIcon.onPrp(stage);
        stage.setWidth(980);
        stage.setHeight(700);

        // No close button on the panel here: Esc closes the window, and a × inside a window that
        // already has one would offer two different meanings for the same gesture.
        var panel = new PrpPanel(settings, false, null);
        var scene = new Scene(panel.root);
        // Nothing else in this window competes for a key, so the panel gets all of them. In the
        // picker the same panel is given only what the projects table is not using.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (Keys.plain(e, javafx.scene.input.KeyCode.ESCAPE)) {
                stage.close();
                e.consume();
                return;
            }
            panel.onKey(e);
        });
        stage.setScene(scene);

        try {
            Files.createDirectories(prpDir);
        } catch (Exception e) {
            // A prp folder that cannot be created is reported by the empty list, not by a modal
            // alert on top of the window that is about to open anyway.
        }
        panel.showProject(PrpPanel.projectOf(prpDir));
        panel.showFocus(true);
        javafx.application.Platform.runLater(panel::focusTree);
        return stage;
    }
}
