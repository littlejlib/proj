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

    /**
     * Whether the window is on screen, readable from any thread.
     * <p>
     * A flag rather than {@code stage.isShowing()} because the caller is the summon key's thread and
     * the answer decides whether to open or to hide - a question that must be answerable without
     * hopping to the FX thread, since the FX thread is not the one that would be blocked either way.
     */
    static volatile boolean showing;

    static void showing(boolean value) {
        showing = value;
    }

    public static boolean isShowing() {
        return showing;
    }

    /**
     * Puts the window away, as the second press of the summon key. Hiding it is exactly a cancel:
     * the stage's hidden handler hands the waiting caller a null result, which is what Esc does.
     */
    public static void hide() {
        var current = instance;
        if (current == null) return;
        Platform.runLater(() -> {
            if (current.stage != null && current.stage.isShowing()) current.stage.hide();
        });
    }

    /** Builds the window without showing it. Called at daemon startup so the first open is cheap too. */
    public static void prewarm(List<Path> browsePaths, Settings settings) {
        Platform.runLater(() -> {
            var t0 = littlejlib.proj.util.Boot.ms();
            try {
                if (instance == null) instance = new ProjectPickerDialog(browsePaths, settings);
                System.out.println("[PickerHost] prewarm built the Stage+Scene in "
                        + (littlejlib.proj.util.Boot.ms() - t0) + "ms   +" + littlejlib.proj.util.Boot.ms() + "ms since boot");
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
        return show(browsePaths, settings, null);
    }

    public static PickerResult show(List<Path> browsePaths, Settings settings, String tab) {
        System.out.println("[PickerHost] show() requested   +" + littlejlib.proj.util.Boot.ms() + "ms since boot");
        return JavaFXManager.getInstance().<PickerResult>showDialog((lock, holder) -> {
            // Reached only once the FX thread gets to this task - queued behind prewarm's own task
            // when this is the very first "open" of a daemon that has not finished warming up yet.
            System.out.println("[PickerHost] show() reached the FX thread   +"
                    + littlejlib.proj.util.Boot.ms() + "ms since boot   (instance built: " + (instance != null) + ")");
            // Settings can change between invocations; a picker built for other roots, or built
            // before a settings change, is discarded rather than shown out of date.
            if (instance != null && (stale || !instance.covers(browsePaths))) {
                instance.dispose();
                instance = null;
            }
            stale = false;
            if (instance == null) instance = new ProjectPickerDialog(browsePaths, settings);
            instance.reopen(lock, holder, tab);
            System.out.println("[PickerHost] window shown   +" + littlejlib.proj.util.Boot.ms() + "ms since boot");
        }, 900_000);
    }

    public static void dispose() {
        var current = instance;
        instance = null;
        if (current != null) current.dispose();
    }
}
