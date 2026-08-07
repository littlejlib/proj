package littlejlib.proj.ide;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real Windows shell icon for an executable, as a JavaFX image.
 * <p>
 * Deliberately does not use SwingFXUtils, which would drag in the javafx.swing module for one
 * conversion; the pixel copy below is the whole of what it would have done.
 */
public final class ExeIcon {

    private ExeIcon() { }

    static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    public static Image load(Path exe, int size) {
        if (exe == null) return null;
        return CACHE.computeIfAbsent(exe.toAbsolutePath() + "@" + size, k -> extract(exe, size));
    }

    static Image extract(Path exe, int size) {
        try {
            var icon = FileSystemView.getFileSystemView().getSystemIcon(exe.toFile(), size, size);
            if (icon == null) return null;

            var buffered = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var g = buffered.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            icon.paintIcon(null, g, 0, 0);
            g.dispose();

            return toFx(buffered);
        } catch (Throwable t) {
            return null;
        }
    }

    static Image toFx(BufferedImage src) {
        var out = new WritableImage(src.getWidth(), src.getHeight());
        var writer = out.getPixelWriter();
        for (var y = 0; y < src.getHeight(); y++) {
            for (var x = 0; x < src.getWidth(); x++) {
                writer.setArgb(x, y, src.getRGB(x, y));
            }
        }
        return out;
    }
}
