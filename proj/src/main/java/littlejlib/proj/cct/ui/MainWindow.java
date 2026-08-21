package littlejlib.proj.cct.ui;

import module java.base;

import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import littlejlib.proj.cct.SettingsStore;
import littlejlib.proj.cct.action.ShellKind;
import littlejlib.proj.cct.search.*;

import static luvjfx.Fx.*;

public final class MainWindow {

    private final DbWorker worker = new DbWorker();
    private final TextField query = new TextField();
    private final ListView<SearchHit> results = new ListView<>();
    private final SessionPane detail = new SessionPane();
    private final ScratchPad scratch = new ScratchPad();
    private final StatusBar status = new StatusBar();
    private final Chips chips = new Chips();
    private final BorderPane root = new BorderPane();
    private final CctHost host;
    private Actions actions;

    private int layer = SettingsStore.get().defaultLayer();
    private MatchMode mode = MatchMode.AUTO;
    private ShellKind shell = ShellKind.preferred();
    private long generation;
    private boolean ready;

    public MainWindow() { this(null); }

    public MainWindow(CctHost host) {
        this.host = host == null ? CctHost.standalone() : host;
        buildTop();
        buildBody();
        root.setBottom(status.node());
        root.setMinWidth(0);
        root.setMinHeight(0);
        results.setCellFactory(HitCell::new);
        results.setPlaceholder(new Label("type to search every claude-code conversation on this machine"));
        results.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> onSelect(b));
        results.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY)
                actions().menu().showAt(results, e.getScreenX(), e.getScreenY());
        });
        detail.list().setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY)
                actions().menu().showAt(detail.list(), e.getScreenX(), e.getScreenY());
        });
        query.textProperty().addListener((o, a, b) -> schedule());
        status.say("opening index…");
    }

    private void buildTop() {
        query.getStyleClass().add("search-field");
        query.setPromptText("what do you half-remember? a token, a folder name, a phrase you typed…");
        var box = vbox().spacing(6).padding(new Insets(10, 12, 8, 12))
            .attr(v -> v.getChildren().addAll(query, chips.node())).node;
        root.setTop(box);
    }

    private void buildBody() {
        results.setMinWidth(0);
        var right = splitPane().attr(s -> {
            s.setOrientation(Orientation.VERTICAL);
            s.getItems().addAll(detail.node(), scratch.node());
            s.setDividerPositions(0.68);
        }).node;
        right.setMinWidth(0);
        var split = splitPane().attr(s -> {
            s.getItems().addAll(results, right);
            s.setDividerPositions(0.46);
        }).node;
        root.setCenter(split);
    }

    public Parent node() { return root; }

    public TextField queryField() { return query; }

    ScratchPad scratch() { return scratch; }

    public void start() {
        worker.open(() -> {
            ready = true;
            chips.set(layer, mode, shell);
            schedule();
        }, msg -> status.say("index unavailable: " + msg));
    }

    public void release() { ready = false; worker.release(); }

    public void close() { ready = false; worker.close(); }

    /** True when the focus is inside this tab, so the host knows whose function keys these are. */
    public boolean holdsFocus() {
        return query.isFocused() || results.isFocused() || detail.list().isFocused()
            || scratch.area().isFocused() || detail.find().holdsFocus();
    }

    private void schedule() {
        if (!ready) return;
        var mine = ++generation;
        var q = new SearchQuery()
            .text(query.getText())
            .layer(layer)
            .mode(mode.name())
            .limit(SettingsStore.get().resultLimit());
        var svc = worker.search();
        worker.run(() -> svc.find(q), hits -> apply(mine, hits), status::say);
    }

    private void apply(long mine, List<SearchHit> hits) {
        if (mine != generation) return;
        results.setItems(FXCollections.observableArrayList(hits));
        if (!hits.isEmpty()) results.getSelectionModel().select(0);
        else detail.clear();
        status.say(hits.size() + (hits.size() == 1 ? " session" : " sessions")
            + (query.getText().isBlank() ? " (most recent first)" : ""));
    }

    private void onSelect(SearchHit hit) {
        if (hit == null || hit.session == null) { detail.clear(); return; }
        loadTurns(hit.session.sessionId(), null);
    }

    /**
     * Loads a session's turns at the CURRENT scope. Called from the selection listener and, just as
     * importantly, directly from {@link #cycleLayer()}: widening the scope has to repaint the turns
     * that are already on screen, and relying on the selection event to do it means relying on the
     * new result list happening to differ from the old one - which, with an empty search box, it
     * does not. That is the shape of the bug where Claude's replies never appeared however many
     * times F9 was pressed.
     */
    private void loadTurns(String sessionId, String note) {
        var svc = worker.search();
        if (svc == null || sessionId == null) return;
        var want = layer;
        worker.run(() -> {
            var s = svc.session(sessionId);
            // Scope 3 is not in the index at all: tool calls and their output are layer 4, which is
            // where nearly all of the 825 MB lives and is deliberately never indexed. Asked for, they
            // are streamed from the session file - the same reader the export uses, so the window and
            // the exported log agree about what happened.
            var turns = want >= 3 && s != null && s.filePath() != null
                ? FileTurns.of(s) : svc.turns(sessionId, want);
            return new Loaded(s, turns);
        }, loaded -> {
            detail.show(loaded.session(), loaded.turns());
            if (note != null) status.say(note + " - " + loaded.turns().size() + " entries");
        }, status::say);
    }

    public Actions actions() {
        if (actions == null)
            actions = new Actions(this, worker, results, detail, scratch, status, query, host);
        return actions;
    }

    void cycleLayer() {
        layer = layer >= 3 ? 1 : layer + 1;
        chips.set(layer, mode, shell);
        schedule();
        var open = detail.current();
        if (open != null) loadTurns(open.sessionId(), "scope: " + Chips.scope(layer));
        else status.say("scope: " + Chips.scope(layer));
    }

    void cycleMode() {
        var all = MatchMode.values();
        mode = all[(mode.ordinal() + 1) % all.length];
        chips.set(layer, mode, shell);
        schedule();
    }

    void cycleShell() {
        shell = shell.next();
        chips.set(layer, mode, shell);
        status.say("F3 now copies the " + shell.label + " form of the resume command");
    }

    int layer() { return layer; }

    ShellKind shell() { return shell; }

    void refresh() { schedule(); }
}
