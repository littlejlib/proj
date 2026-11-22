package littlejlib.proj.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import littlejlib.proj.hib.Suggest;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks the human which suggested conversations to hibernate. It pops up over whatever is being typed,
 * so every key is ignored for the first 2 seconds, approval is F2 only, Esc cancels.
 */
public final class HibApprovalWindow {

    static final int ARM_SECONDS = 2;

    final List<Suggest.Candidate> candidates;
    final String askedBy;
    final List<CheckBox> boxes = new ArrayList<>();
    final Button approve = new Button(), cancel = new Button("Esc  Cancel");
    Stage stage;
    long shownAt;
    boolean done;

    HibApprovalWindow(List<Suggest.Candidate> candidates, String askedBy) {
        this.candidates = candidates;
        this.askedBy = askedBy;
    }

    /** The session ids the human approved, empty when cancelled, null when no window could be shown. */
    public static List<String> ask(List<Suggest.Candidate> candidates, String askedBy) {
        return JavaFXManager.getInstance().<List<String>>showDialog((lock, result) ->
                new HibApprovalWindow(candidates, askedBy).show(lock, result), 15 * 60_000L);
    }

    void show(Object lock, Object[] result) {
        stage = new Stage();
        stage.setTitle("proj - hibernate conversations?");
        var head = new Label("Hibernate these claude-code conversations? Each one's log is exported and its tab "
                + "closed; any of them comes back with  proj hib restore <name>.");
        head.setWrapText(true);
        var from = new Label("Asked by: " + askedBy);
        from.setStyle("-fx-text-fill: #546E7A;");
        var list = new VBox(6);
        for (var c : candidates) list.getChildren().add(row(c));
        var scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        approve.setDisable(true);
        approve.setOnAction(e -> finish(lock, result, true));
        cancel.setOnAction(e -> finish(lock, result, false));
        var tick = new Label("F3 ticks or unticks the row with focus (arrows move); a click does too. Typing does nothing here.");
        tick.setStyle("-fx-text-fill: #546E7A;");
        var buttons = new HBox(8, approve, cancel, tick);
        buttons.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var root = new VBox(10, head, from, scroll, buttons);
        root.setPadding(new Insets(14));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Keys.noMnemonics(root);
        var scene = new Scene(root, 760, Math.min(560, 190 + 44 * candidates.size()));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> onKey(e, lock, result));
        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> { if (!navigation(e.getCode())) e.consume(); });
        scene.addEventFilter(KeyEvent.KEY_TYPED, KeyEvent::consume);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.setOnHidden(e -> finish(lock, result, false));
        stage.focusedProperty().addListener((o, was, now) -> { if (now) arm(); });
        stage.show();
        stage.toFront();
        arm();
        if (!boxes.isEmpty()) boxes.get(0).requestFocus();
    }

    /**
     * Restarts the guard. Called on show and again whenever the window regains focus, because it
     * takes focus while the human may be typing somewhere else.
     */
    void arm() {
        shownAt = System.currentTimeMillis();
        approve.setDisable(true);
        countdown();
    }

    static boolean navigation(KeyCode k) {
        return k == KeyCode.UP || k == KeyCode.DOWN || k == KeyCode.TAB;
    }

    CheckBox row(Suggest.Candidate c) {
        var s = c.session();
        var text = s.label() + "   " + c.reason() + (c.onPrp() ? "   PRP " + s.prpIndex()
                : "?".equals(s.prpIndex()) ? "   (PRP uncertain: ticking it hibernates without one, log goes to the hibernated folder)"
                : "   (no PRP: ticking it hibernates anyway, log goes to the hibernated folder)");
        var box = new CheckBox(text + "\n" + s.cwd());
        box.setSelected(c.onPrp());
        box.setUserData(s.sessionId());
        boxes.add(box);
        return box;
    }

    void countdown() {
        approve.setText("F2  Hibernate ticked (wait " + ARM_SECONDS + " s)");
        var t = new Timeline(new KeyFrame(Duration.seconds(ARM_SECONDS), e -> {
            if (!armed()) return;
            approve.setDisable(false);
            approve.setText("F2  Hibernate ticked");
        }));
        t.play();
    }

    boolean armed() {
        return System.currentTimeMillis() - shownAt >= ARM_SECONDS * 1000L;
    }

    /**
     * Typing must never change anything here: Space would tick a checkbox, so letters, digits and
     * Space are swallowed. F3 ticks, F2 approves, Esc cancels, arrows move.
     */
    void onKey(KeyEvent e, Object lock, Object[] result) {
        var k = e.getCode();
        if (navigation(k)) return;
        e.consume();
        if (!armed()) return;
        if (k == KeyCode.F2) finish(lock, result, true);
        else if (k == KeyCode.ESCAPE) finish(lock, result, false);
        else if (k == KeyCode.F3 && stage.getScene().getFocusOwner() instanceof CheckBox b) b.setSelected(!b.isSelected());
    }

    void finish(Object lock, Object[] result, boolean approved) {
        if (done) return;
        done = true;
        var ids = new ArrayList<String>();
        if (approved) for (var b : boxes) if (b.isSelected()) ids.add((String) b.getUserData());
        stage.hide();
        synchronized (lock) {
            result[0] = ids;
            lock.notifyAll();
        }
    }
}
