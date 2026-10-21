package littlejlib.proj.ui;

import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * A legend entry that is also the control.
 */
public final class Legend {

    private Legend() { }

    public static Label of(String text, String style, Runnable action) {
        return clickable(Chip.of(text, style), action);
    }

    public static Label of(String text, String style, String glyph, String glyphColour, Runnable action) {
        return clickable(Chip.of(text, style, glyph, glyphColour), action);
    }

    static Label clickable(Label chip, Runnable action) {
        if (action == null) return chip;
        chip.setCursor(Cursor.HAND);
        chip.setOnMouseClicked(e -> {
            action.run();
            e.consume();
        });
        return chip;
    }

    public static Label withTip(Label chip, String tip) {
        if (tip != null && !tip.isBlank()) chip.setTooltip(new Tooltip(tip));
        return chip;
    }
}
