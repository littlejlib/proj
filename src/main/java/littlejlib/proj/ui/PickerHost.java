package littlejlib.proj.ui;

import javafx.application.Platform;
import littlejlib.proj.Settings;

import java.nio.file.Path;
import java.util.List;

/**
 * Keeps one picker window alive across invocations.
 * <p>
 * Measured, and the reason this class exists: with the JavaFX platform already warm in the daemon,
 * the FIRST Stage plus Scene still took about four seconds - toolkit, fonts and CSS all load on
 * demand. Reusing the window turns every subsequent open into a show(). A daemon that only keeps
 * the platform warm and rebuilds the window each time is barely faster than no daemon at all.
 */
public final class PickerHost {

    private PickerHost() { }

    static volatile ProjectPickerDialog instance;

    /** Builds the window without showing it. Called at daemon startup so the first open is cheap too. */
    public static void prewarm(List<Path> browsePaths, Settings settings) {
        Platform.runLater(() -> {
            try {
                if (instance == null) instance = new ProjectPickerDialog(browsePaths, settings);
            } catch (Exception e) {
                System.err.println("[PickerHost] prewarm failed: " + e.getMessage());
            }
        });
    }

    public static PickerResult show(List<Path> browsePaths, Settings settings) {
        return JavaFXManager.getInstance().<PickerResult>showDialog((lock, holder) -> {
            // Settings can change between invocations; a picker built for other roots is discarded.
            if (instance != null && !instance.covers(browsePaths)) {
                instance.dispose();
                instance = null;
            }
            if (instance == null) instance = new ProjectPickerDialog(browsePaths, settings);
            instance.reopen(lock, holder);
        }, 900_000);
    }

    public static void dispose() {
        var current = instance;
        instance = null;
        if (current != null) current.dispose();
    }
}
