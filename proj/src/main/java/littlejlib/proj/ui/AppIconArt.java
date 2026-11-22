package littlejlib.proj.ui;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * The one drawing of the proj mark, so that the tray icon and every window icon are the same thing.
 * <p>
 * Drawn rather than shipped as a .png because it has to hold up at seven sizes: the letter is
 * placed by its own visual bounds at each one, which is what a scaled 16px bitmap cannot do. Orange
 * marks the PRP windows - same mark, so it still reads as proj; different tile, so a taskbar with
 * the picker and a PRP manager both open is unambiguous.
 */
public final class AppIconArt {

    private AppIconArt() { }

    public static final Color
            PROJECTS = new Color(41, 98, 255),
            PRP = new Color(245, 124, 0);

    public static BufferedImage draw(int size, Color tile) {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(tile);
        var corner = Math.max(3, Math.round(size * 0.28f));
        g.fillRoundRect(0, 0, size, size, corner, corner);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, Math.round(size * 0.72f))));
        var glyph = g.getFont().createGlyphVector(g.getFontRenderContext(), "p");
        var bounds = glyph.getVisualBounds();
        g.drawGlyphVector(glyph,
                (float) ((size - bounds.getWidth()) / 2 - bounds.getX()),
                (float) ((size - bounds.getHeight()) / 2 - bounds.getY()));

        g.dispose();
        return img;
    }
}
