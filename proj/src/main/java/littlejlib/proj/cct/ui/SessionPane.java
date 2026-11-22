package littlejlib.proj.cct.ui;

import module java.base;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import littlejlib.proj.cct.action.ResumeCommands;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.search.Filters;
import littlejlib.proj.cct.store.*;

import static luvjfx.Fx.*;
import static littlejlib.proj.cct.Num.*;

public final class SessionPane {

    private final Label heading = new Label();
    private final Label path = new Label();
    private final Label ident = new Label();
    private final Label facts = new Label();
    private final Label warn = new Label();
    private final ListView<TurnDoc> turns = new ListView<>();
    private final TurnFind find = new TurnFind(turns);
    private final VBox root;
    private SessionDoc current;

    public SessionPane() {
        style(heading, "hit-title");
        style(path, "hit-path");
        style(ident, "hit-facts");
        style(facts, "hit-facts");
        style(warn, "warn-note");
        turns.setCellFactory(v -> new TurnCell(turns));
        // MULTIPLE so a decision spread over four turns can be copied or exported as one block.
        // Shift and Ctrl are chords, which this user should not need: Alt+A takes the whole list.
        turns.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        turns.setPlaceholder(new Label("select a session on the left, then press F2 to read its turns"));
        VBox.setVgrow(turns, Priority.ALWAYS);
        var head = vbox().spacing(2).padding(new Insets(10, 12, 8, 12))
            .attr(v -> v.getChildren().addAll(heading, path, ident, facts, warn)).node;
        root = vbox().attr(v -> v.getChildren().addAll(head, find.node(), turns)).node;
        root.setMinWidth(0);
        head.setMinWidth(0);
        turns.setMinWidth(0);
        var text = root.widthProperty().subtract(30);
        for (var l : List.of(heading, path, ident, facts, warn)) l.maxWidthProperty().bind(text);
    }

    private static void style(Label l, String cls) {
        l.getStyleClass().add(cls);
        l.setWrapText(true);
        l.setMnemonicParsing(false);
    }

    public Node node() { return root; }

    public SessionDoc current() { return current; }

    public ListView<TurnDoc> list() { return turns; }

    public TurnFind find() { return find; }

    public void clear() {
        current = null;
        for (var l : List.of(heading, path, ident, facts, warn)) l.setText("");
        warn.setManaged(false);
        turns.setItems(FXCollections.observableArrayList());
        find.reset();
    }

    public void show(SessionDoc s, List<TurnDoc> loaded) {
        current = s;
        if (s == null) { clear(); return; }
        heading.setText(s.aiTitle() == null || s.aiTitle().isBlank() ? "(untitled session)" : s.aiTitle());
        path.setText(s.cwd() == null || s.cwd().isBlank() ? "(no folder recorded)" : s.cwd());
        ident.setText("session id   " + s.sessionId());
        facts.setText(factLine(s, loaded));
        var note = ResumeCommands.note(s);
        warn.setText(note.isBlank() ? "" : "! " + note);
        warn.setManaged(!note.isBlank());
        warn.setVisible(!note.isBlank());
        turns.setItems(FXCollections.observableArrayList(loaded));
        if (!turns.getItems().isEmpty()) turns.getSelectionModel().select(0);
        find.reset();
    }

    public void selectAll() { turns.getSelectionModel().selectAll(); }

    public List<TurnDoc> selectedTurns() {
        var picked = turns.getSelectionModel().getSelectedItems();
        return picked.isEmpty() ? List.of() : new ArrayList<>(picked);
    }

    /**
     * The selection as text. Every turn carries its speaker and its local time, because these get
     * pasted somewhere with no other context - and one line per paragraph, never wrapped.
     */
    public String selectedTurnText() {
        var picked = selectedTurns();
        if (picked.isEmpty()) return null;
        if (picked.size() == 1) return picked.get(0).text();
        var out = new ArrayList<String>();
        for (var t : picked) {
            out.add("[" + speaker(t) + "] " + Timestamps.local(n(t.timestamp())));
            out.add(t.text() == null ? "" : t.text());
            out.add("");
        }
        return String.join("\n", out);
    }

    public String transcriptText() {
        if (current == null) return null;
        var out = new ArrayList<String>();
        out.add(heading.getText());
        out.add(path.getText());
        out.add("session id   " + current.sessionId());
        out.add("");
        for (var t : turns.getItems()) {
            out.add("[" + speaker(t) + "] " + Timestamps.local(n(t.timestamp())));
            out.add(t.text() == null ? "" : t.text());
            out.add("");
        }
        return String.join("\n", out);
    }

    static String speaker(TurnDoc t) {
        var layer = Layer.ofDepth(n(t.layer()));
        return layer == Layer.HUMAN ? "YOU" : layer.label.toUpperCase(Locale.ROOT);
    }

    private static String factLine(SessionDoc s, List<TurnDoc> loaded) {
        var parts = new ArrayList<String>();
        parts.add(Timestamps.local(n(s.lastActivity())));
        if (n(s.transcriptMissing())) parts.add("prompts only (transcript pruned)");
        else parts.add(count(n(s.recordCount())) + " records");
        parts.add(loaded.size() + " turns shown");
        if (n(s.fileBytes()) > 0) parts.add(bytes(n(s.fileBytes())));
        parts.add(n(s.humanTurns()) + " of your turns");
        var tok = Filters.totalTokens(s);
        if (tok > 0) parts.add(count(tok) + " tokens");
        if (n(s.imageLineBytes()) > 0)
            parts.add(bytes(n(s.imageLineBytes())) + " of " + bytes(n(s.fileBytes())) + " is images");
        if (s.models() != null) parts.add(s.models());
        if (s.gitBranch() != null) parts.add("branch " + s.gitBranch());
        return String.join("   ·   ", parts);
    }
}
