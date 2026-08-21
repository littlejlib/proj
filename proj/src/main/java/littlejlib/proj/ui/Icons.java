package littlejlib.proj.ui;

import javafx.scene.control.Label;

/**
 * An action's glyph as a node, so it can be sized independently of the text it sits beside.
 * <p>
 * Both the row buttons and the legend chips run their text at 9px, which is the right size for
 * nine buttons in one cell and far too small for a pictogram - an icon drawn at 9px is a smudge
 * that has to be read as carefully as the word it was meant to save you reading. Set as a graphic
 * it keeps its own font size.
 * <p>
 * JavaFX has no colour-font support, so these arrive as monochrome outlines whatever the emoji is.
 * That is fine here and deliberate: the colour on these controls comes from the chip background,
 * which is what carries the action's identity, and a black glyph reads on every one of them.
 * <p>
 * A glyph the platform font lacks is drawn as an empty box with no warning of any kind. Run
 * {@code SmokeIcons} before adding one - it renders every icon at 28px offscreen, which is the only
 * way to tell a missing glyph from a present one.
 */
public final class Icons {

    private Icons() { }

    public static Label of(String glyph, double px) {
        return of(glyph, px, null);
    }

    /** {@code colour} paints the glyph, because a monochrome outline takes the text fill. */
    public static Label of(String glyph, double px, String colour) {
        var l = new Label(glyph);
        l.setStyle("-fx-font-size: " + px + "px;"
                + (colour == null ? "" : " -fx-text-fill: " + colour + ";"));
        return l;
    }
}
