package littlejlib.proj.util;

import javafx.scene.image.*;

import java.awt.image.BufferedImage;

/**
 * AWT to JavaFX in one bulk copy. Deliberately not SwingFXUtils, which would drag in the whole
 * javafx.swing module for exactly this.
 */
public final class FxImage {

    private FxImage() { }

    public static Image of(BufferedImage src) {
        var w = src.getWidth();
        var h = src.getHeight();
        var out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(),
                src.getRGB(0, 0, w, h, null, 0, w), 0, w);
        return out;
    }
}
