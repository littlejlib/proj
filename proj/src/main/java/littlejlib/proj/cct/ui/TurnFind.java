package littlejlib.proj.cct.ui;

import module java.base;

import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import littlejlib.proj.cct.model.TurnDoc;

import static luvjfx.Fx.*;

/**
 * Plain text search INSIDE the open transcript, which is a different question from the search at the
 * top of the window: that one asks which conversation, this one asks where in it. Hidden until asked
 * for, so it never competes for the eye or for the Tab order.
 * <p>
 * Enter steps forward, Shift+Enter back, Esc leaves and puts it away. It moves the turn selection
 * rather than painting a highlight, because the selection is what every copy and export action here
 * already reads.
 */
public final class TurnFind {

    private final TextField box = new TextField();
    private final Label count = new Label();
    private final HBox bar;
    private final ListView<TurnDoc> turns;
    private final List<Integer> hits = new ArrayList<>();
    private int at = -1;

    public TurnFind(ListView<TurnDoc> turns) {
        this.turns = turns;
        box.setPromptText("find in this conversation — Enter next, Shift+Enter previous, Esc close");
        box.getStyleClass().add("chip-field");
        HBox.setHgrow(box, Priority.ALWAYS);
        count.getStyleClass().add("legend-label");
        box.textProperty().addListener((o, a, b) -> recompute(b));
        box.setOnKeyPressed(this::onKey);
        bar = hbox().spacing(8).alignment(Pos.CENTER_LEFT).padding(new Insets(4, 12, 4, 12))
            .attr(h -> h.getChildren().addAll(box, count)).node;
        bar.setMinWidth(0);
        hide();
    }

    public Node node() { return bar; }

    public boolean visible() { return bar.isVisible(); }

    public boolean holdsFocus() { return box.isFocused(); }

    public void show() {
        bar.setVisible(true);
        bar.setManaged(true);
        box.requestFocus();
        box.selectAll();
        recompute(box.getText());
    }

    public void hide() {
        bar.setVisible(false);
        bar.setManaged(false);
        hits.clear();
        at = -1;
        count.setText("");
    }

    /** Called when a different session is loaded: the old match list means nothing now. */
    public void reset() {
        hits.clear();
        at = -1;
        if (visible()) recompute(box.getText());
    }

    private void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case ENTER -> { step(e.isShiftDown() ? -1 : 1); e.consume(); }
            case ESCAPE -> { hide(); turns.requestFocus(); e.consume(); }
            case DOWN -> { step(1); e.consume(); }
            case UP -> { step(-1); e.consume(); }
            default -> { }
        }
    }

    private void recompute(String text) {
        hits.clear();
        at = -1;
        if (text == null || text.isBlank()) { count.setText(""); return; }
        var needle = text.toLowerCase(Locale.ROOT);
        var items = turns.getItems();
        for (var i = 0; i < items.size(); i++) {
            var t = items.get(i);
            if (t.text() != null && t.text().toLowerCase(Locale.ROOT).contains(needle)) hits.add(i);
        }
        if (hits.isEmpty()) { count.setText("no match"); return; }
        step(1);
    }

    private void step(int direction) {
        if (hits.isEmpty()) { count.setText(box.getText().isBlank() ? "" : "no match"); return; }
        at = at < 0 ? (direction > 0 ? 0 : hits.size() - 1)
                    : (at + direction + hits.size()) % hits.size();
        var row = hits.get(at);
        turns.getSelectionModel().clearAndSelect(row);
        turns.scrollTo(Math.max(0, row - 1));
        count.setText((at + 1) + " of " + hits.size());
    }
}
