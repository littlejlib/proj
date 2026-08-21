package littlejlib.proj.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;

/**
 * A key, an icon and a name on a coloured background.
 * <p>
 * The one visual idiom this application repeats: the legend at the bottom of a window, the items of
 * the row context menu and the buttons revealed under a PRP all wear the same tint for the same
 * action, so the legend is read once and everything else is recognised by colour and shape from
 * then on. It lived in three files at three sizes before this.
 */
public final class Chip {

    private Chip() { }

    public static Label of(String text, String style) {
        return sized(text, style, 9);
    }

    public static Label of(String text, String style, String glyph, String glyphColour) {
        var chip = sized(text, style, 9);
        chip.setGraphic(Icons.of(glyph, 13, glyphColour));
        chip.setGraphicTextGap(4);
        return chip;
    }

    public static Label sized(String text, String style, double px) {
        var chip = new Label(text);
        chip.setStyle("-fx-font-size: " + px + "px; -fx-padding: 1 5 1 5; -fx-background-radius: 3;" + style);
        return chip;
    }

    /** The menu variant: larger, because a menu has the room a legend line does not. */
    public static Label menu(String text, String style, Node icon) {
        var chip = sized(text, style, 11);
        chip.setStyle("-fx-font-size: 11px; -fx-padding: 2 9 2 7; -fx-background-radius: 3;" + style);
        chip.setGraphic(icon);
        chip.setGraphicTextGap(7);
        return chip;
    }
}
