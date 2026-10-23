package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import littlejlib.proj.Settings;
import littlejlib.proj.hib.*;

import java.util.List;
import java.util.Locale;

import static littlejlib.proj.hib.ProcMem.mb;

/**
 * Running claude-code conversations with their RAM, and the hibernated ones, in one tab. Built on
 * first selection; nothing is read until it is shown.
 */
public final class ConversationsTab {

    final Settings settings;
    final TextField filter = new TextField();
    final TableView<LiveSession> running = ConversationTables.running();
    final TableView<HibRecord> parked = ConversationTables.parked();
    final Label runningHead = head(), parkedHead = head(), status = new Label();
    final ConversationActions actions;
    List<LiveSession> live = List.of();
    List<HibRecord> records = List.of();
    ParkedView view = ParkedView.HIBERNATED;
    BorderPane root;

    public ConversationsTab(Settings settings) {
        this.settings = settings;
        this.actions = new ConversationActions(settings, this::refresh, status::setText);
        status.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;");
    }

    public Parent build() {
        if (root != null) return root;
        filter.setPromptText("filter by name, folder or PRP");
        filter.textProperty().addListener((o, a, b) -> show());
        var lists = new VBox(4, runningHead, running, parkedHead, parked);
        VBox.setVgrow(running, Priority.ALWAYS);
        VBox.setVgrow(parked, Priority.ALWAYS);
        root = new BorderPane(lists);
        root.setTop(filter);
        root.setBottom(legend());
        BorderPane.setMargin(filter, new Insets(8, 10, 6, 10));
        BorderPane.setMargin(lists, new Insets(0, 10, 0, 10));
        return root;
    }

    VBox legend() {
        var keys = new HBox(4);
        for (var k : ConversationKey.values())
            keys.getChildren().add(Legend.withTip(Legend.of(k.label, k.style, () -> run(k)), k.tip));
        var box = new VBox(4, status, keys);
        box.setPadding(new Insets(6, 10, 8, 10));
        return box;
    }

    public void refresh() {
        runningHead.setText("Running - reading…");
        Thread.ofVirtual().name("conversations-refresh").start(() -> {
            var now = LiveSessions.all();
            var hib = HibStore.all();
            Platform.runLater(() -> { live = now; records = hib; show(); });
            LiveSessions.withDetail(now);
            Platform.runLater(() -> { running.refresh(); show(); });
        });
    }

    void show() {
        var f = filter.getText() == null ? "" : filter.getText().strip().toLowerCase(Locale.ROOT);
        var shownLive = live.stream().filter(s -> f.isEmpty() || text(s).contains(f)).toList();
        var shownHib = records.stream().filter(view::shows).filter(r -> f.isEmpty() || text(r).contains(f)).toList();
        keep(running, shownLive);
        keep(parked, shownHib);
        var ram = live.stream().mapToLong(s -> s.workingSet() == null ? 0 : s.workingSet()).sum();
        var commit = live.stream().mapToLong(s -> s.privateBytes() == null ? 0 : s.privateBytes()).sum();
        runningHead.setText("Running: " + live.size() + " conversations, " + mb(ram) + " MB RAM, "
                + mb(commit) + " MB committed");
        parkedHead.setText("Hibernated (" + view.label + "): " + shownHib.size() + "   - F6 changes the view");
    }

    static <T> void keep(TableView<T> table, List<T> rows) {
        var at = table.getSelectionModel().getSelectedIndex();
        table.setItems(FXCollections.observableArrayList(rows));
        if (!rows.isEmpty()) table.getSelectionModel().select(Math.max(0, Math.min(at, rows.size() - 1)));
    }

    public void focusSearch() {
        filter.requestFocus();
    }

    public void onKey(KeyEvent e) {
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;
        var k = ConversationKey.of(e.getCode());
        if (k != null) { run(k); e.consume(); return; }
        if (e.getCode() == KeyCode.ENTER && parked.isFocused()) { run(ConversationKey.RESTORE); e.consume(); return; }
        if (e.getCode() == KeyCode.DOWN && filter.isFocused()) {
            if (running.getItems().isEmpty()) into(parked, 0); else into(running, 0);
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.DOWN && running.isFocused() && atEnd(running)) { into(parked, 0); e.consume(); return; }
        if (e.getCode() == KeyCode.UP && parked.isFocused() && parked.getSelectionModel().getSelectedIndex() <= 0) {
            into(running, running.getItems().size() - 1); e.consume(); return;
        }
        if (e.getCode() == KeyCode.UP && running.isFocused() && running.getSelectionModel().getSelectedIndex() <= 0) {
            filter.requestFocus(); e.consume(); return;
        }
        var typed = e.getText();
        if (!filter.isFocused() && typed != null && typed.length() == 1 && Character.isLetterOrDigit(typed.charAt(0))) {
            filter.requestFocus();
            filter.appendText(typed);
            e.consume();
        }
    }

    void run(ConversationKey k) {
        switch (k) {
            case LOG -> actions.openLog(parked.getSelectionModel().getSelectedItem());
            case REFRESH -> refresh();
            case VIEW -> { view = view.next(); show(); }
            case ANYWAY -> actions.hibernate(running.getSelectionModel().getSelectedItem(), true);
            case HIBERNATE -> actions.hibernate(running.getSelectionModel().getSelectedItem(), false);
            case RESTORE -> actions.restore(parked.getSelectionModel().getSelectedItem());
        }
    }

    static <T> void into(TableView<T> t, int row) {
        t.requestFocus();
        if (!t.getItems().isEmpty()) t.getSelectionModel().select(Math.max(0, row));
    }

    static boolean atEnd(TableView<?> t) {
        return t.getSelectionModel().getSelectedIndex() >= t.getItems().size() - 1;
    }

    static String text(LiveSession s) {
        return (s.label() + " " + s.cwd() + " " + s.prpIndex()).toLowerCase(Locale.ROOT);
    }

    static String text(HibRecord r) {
        return (r.label() + " " + r.cwd() + " " + r.prpLabel() + " " + r.sessionId()).toLowerCase(Locale.ROOT);
    }

    static Label head() {
        var l = new Label();
        l.setStyle("-fx-font-weight: bold; -fx-padding: 4 0 2 0;");
        return l;
    }
}
