package littlejlib.proj.ide;

import javafx.scene.image.Image;
import littlejlib.proj.util.FxImage;

import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real Windows shell icon for an executable, as a JavaFX image.
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

            return FxImage.of(buffered);
        } catch (Throwable t) {
            return null;
        }
    }
}
