package littlejlib.proj.ui;

import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import littlejlib.proj.util.FxImage;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The window icon, from the same drawing as the tray icon.
 * <p>
 * Windows picks a size per use - 16 in the title bar, 32 in the taskbar, larger again for alt-tab -
 * and takes it from whatever the stage offers, so every size is offered rather than one scaled badly.
 * <p>
 * An owned window does NOT inherit its owner's icons, so every Stage and every control Dialog is
 * passed through here. One window missed is the generic Java mark sitting in a taskbar of blue.
 */
public final class AppIcon {

    private AppIcon() { }

    static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    static final Map<Color, List<Image>> CACHE = new ConcurrentHashMap<>();

    public static void on(Stage stage) { set(stage, AppIconArt.PROJECTS); }

    public static void onPrp(Stage stage) { set(stage, AppIconArt.PRP); }

    public static void on(Dialog<?> dialog) { onDialog(dialog, AppIconArt.PROJECTS); }

    public static void onPrp(Dialog<?> dialog) { onDialog(dialog, AppIconArt.PRP); }

    public static List<Image> of(Color tile) {
        return CACHE.computeIfAbsent(tile, t -> Arrays.stream(SIZES)
                .mapToObj(size -> FxImage.of(AppIconArt.draw(size, t))).toList());
    }

    static void set(Stage stage, Color tile) {
        if (stage != null) stage.getIcons().setAll(of(tile));
    }

    /** A Dialog's window exists before it is shown in every JavaFX seen so far; setOnShown is the belt. */
    static void onDialog(Dialog<?> dialog, Color tile) {
        var stage = windowOf(dialog);
        if (stage != null) set(stage, tile);
        else dialog.setOnShown(e -> set(windowOf(dialog), tile));
    }

    static Stage windowOf(Dialog<?> dialog) {
        var scene = dialog.getDialogPane().getScene();
        return scene != null && scene.getWindow() instanceof Stage s ? s : null;
    }
}
