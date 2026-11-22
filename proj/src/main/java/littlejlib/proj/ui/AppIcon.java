package littlejlib.proj.ui;

import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The window icon, from the same PNG files as the tray icon and the exe.
 * <p>
 * Windows picks a size per use - 16 in the title bar, 32 in the taskbar, larger again for alt-tab -
 * and takes it from whatever the stage offers, so every size is offered rather than one scaled badly.
 * <p>
 * An owned window does NOT inherit its owner's icons, so every Stage and every control Dialog is
 * passed through here. One window missed is the generic Java mark sitting in a taskbar of blue.
 * <p>
 * The files are drawn once by the test-side {@code IconGen}, not at run time: drawing needs AWT.
 */
public final class AppIcon {

    private AppIcon() { }

    public static final String PROJECTS = "proj", PRP = "prp";

    static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    static final Map<String, List<Image>> CACHE = new ConcurrentHashMap<>();

    public static void on(Stage stage) { set(stage, PROJECTS); }

    public static void onPrp(Stage stage) { set(stage, PRP); }

    public static void on(Dialog<?> dialog) { onDialog(dialog, PROJECTS); }

    public static void onPrp(Dialog<?> dialog) { onDialog(dialog, PRP); }

    public static List<Image> of(String tile) {
        return CACHE.computeIfAbsent(tile, t -> Arrays.stream(SIZES).mapToObj(size -> load(t, size))
                .filter(Objects::nonNull).toList());
    }

    /** The PNG for this tile at the smallest drawn size that is at least {@code px}. */
    public static InputStream stream(String tile, int px) {
        var size = Arrays.stream(SIZES).filter(s -> s >= px).findFirst().orElse(SIZES[SIZES.length - 1]);
        return AppIcon.class.getResourceAsStream("icon/" + tile + "-" + size + ".png");
    }

    static Image load(String tile, int size) {
        try (var in = stream(tile, size)) {
            return in == null ? null : new Image(in);
        } catch (IOException e) {
            return null;
        }
    }

    static void set(Stage stage, String tile) {
        if (stage != null) stage.getIcons().setAll(of(tile));
    }

    /** A Dialog's window exists before it is shown in every JavaFX seen so far; setOnShown is the belt. */
    static void onDialog(Dialog<?> dialog, String tile) {
        var stage = windowOf(dialog);
        if (stage != null) set(stage, tile);
        else dialog.setOnShown(e -> set(windowOf(dialog), tile));
    }

    static Stage windowOf(Dialog<?> dialog) {
        var scene = dialog.getDialogPane().getScene();
        return scene != null && scene.getWindow() instanceof Stage s ? s : null;
    }
}
