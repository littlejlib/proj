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
    static volatile boolean stale;

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

    /**
     * "Rebuild before showing this again."
     * <p>
     * Columns, IDE buttons and terminals are all read from settings when the window is CONSTRUCTED,
     * and this class exists precisely to construct it once. So a settings change would otherwise not
     * be visible until the daemon was restarted - "reopen the picker" being advice the user follows
     * and which then does nothing. Deferred rather than acted on immediately because the settings
     * dialog is usually opened FROM the picker, and disposing a window while it is on screen is not
     * a thing to do; the next show() picks this up, when the old window is already hidden.
     */
    public static void invalidate() {
        stale = true;
    }

    public static PickerResult show(List<Path> browsePaths, Settings settings) {
        return JavaFXManager.getInstance().<PickerResult>showDialog((lock, holder) -> {
            // Settings can change between invocations; a picker built for other roots, or built
            // before a settings change, is discarded rather than shown out of date.
            if (instance != null && (stale || !instance.covers(browsePaths))) {
                instance.dispose();
                instance = null;
            }
            stale = false;
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
