package littlejlib.proj.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A row of items that is BOTH the legend and the control.
 */
public final class PaneStrip {

    public final HBox root = new HBox(4);

    final List<Label> chips = new ArrayList<>();
    final List<Runnable> actions = new ArrayList<>();
    int current;
    boolean focused = true;

    public PaneStrip() {
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(3, 6, 3, 6));
    }

    public PaneStrip add(String label, String key, String tooltip, Runnable action) {
        var index = chips.size();
        var chip = Chip.of(label + "   " + key, Palette.GREY);
        chip.setCursor(Cursor.HAND);
        if (tooltip != null && !tooltip.isBlank()) chip.setTooltip(new Tooltip(tooltip + "   (" + key + ")"));
        chip.setOnMouseClicked(e -> {
            run(index);
            e.consume();
        });
        chips.add(chip);
        actions.add(action);
        root.getChildren().add(chip);
        repaint();
        return this;
    }

    /** Select and invoke - the one path a click and a key both take. */
    public void run(int index) {
        if (index < 0 || index >= chips.size()) return;
        current = index;
        repaint();
        var action = actions.get(index);
        if (action != null) action.run();
    }

    public void select(int index) {
        if (index < 0 || index >= chips.size()) return;
        current = index;
        repaint();
    }

    /** Left and Right along the strip, wrapping - the arrow half of the keyboard model. */
    public void step(int delta) {
        if (chips.isEmpty()) return;
        run(Math.floorMod(current + delta, chips.size()));
    }

    public int current() {
        return current;
    }

    public int size() {
        return chips.size();
    }

    public void focused(boolean value) {
        focused = value;
        repaint();
    }

    void repaint() {
        for (var i = 0; i < chips.size(); i++) {
            var active = i == current;
            chips.get(i).setStyle("-fx-font-size: 9px; -fx-padding: 2 7 2 7; -fx-background-radius: 3;"
                    + (active ? Palette.BLUE : Palette.GREY)
                    + (active ? "-fx-font-weight: bold;" : ""));
            chips.get(i).setOpacity(focused ? 1.0 : 0.45);
        }
    }
}
