package littlejlib.proj.cct.ui;

import module java.base;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import littlejlib.proj.cct.model.SessionDoc;
import littlejlib.proj.cct.search.*;
import littlejlib.proj.cct.store.Timestamps;

import static littlejlib.proj.cct.Num.*;

public final class HitCell extends ListCell<SearchHit> {

    private final Label title = styled("hit-title");
    private final Label path = styled("hit-path");
    private final Label facts = styled("hit-facts");
    private final Label snippet = styled("hit-snippet");
    private final VBox box = new VBox(2, title, path, facts, snippet);

    public HitCell(ListView<SearchHit> owner) {
        var width = owner.widthProperty().subtract(34);
        for (var l : List.of(title, path, facts, snippet)) l.maxWidthProperty().bind(width);
        box.maxWidthProperty().bind(width);
        setText(null);
        setGraphic(box);
    }

    private static Label styled(String cls) {
        var l = new Label();
        l.getStyleClass().add(cls);
        l.setMnemonicParsing(false);
        l.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        return l;
    }

    @Override
    protected void updateItem(SearchHit h, boolean empty) {
        super.updateItem(h, empty);
        if (empty || h == null || h.session == null) { setGraphic(null); return; }
        setGraphic(box);
        var s = h.session;
        title.setText(titleOf(s));
        path.setText(s.cwd() == null || s.cwd().isBlank() ? "(no directory recorded)" : s.cwd());
        facts.setText(factsOf(h, s));
        snippet.setText(Snippets.oneLine(h.snippet, 300));
        snippet.setVisible(!snippet.getText().isEmpty());
        snippet.setManaged(snippet.isVisible());
    }

    private static String titleOf(SessionDoc s) {
        var t = s.aiTitle();
        if (t == null || t.isBlank()) t = Snippets.oneLine(s.firstHumanPrompt(), 80);
        return t == null || t.isBlank() ? "(untitled session)" : t;
    }

    private static String factsOf(SearchHit h, SessionDoc s) {
        var parts = new ArrayList<String>();
        var id = s.sessionId();
        parts.add(id == null ? "?" : id.substring(0, Math.min(8, id.length())));
        parts.add(Timestamps.local(n(s.lastActivity())) + "  " + Timestamps.ago(n(s.lastActivity())));
        if (n(s.transcriptMissing())) {
            parts.add("prompts only — transcript pruned");
            parts.add(n(s.humanTurns()) + " prompts");
        } else {
            parts.add(count(n(s.recordCount())) + " rec");
            parts.add(bytes(n(s.fileBytes())));
            parts.add(n(s.humanTurns()) + " human");
        }
        var tok = Filters.totalTokens(s);
        if (tok > 0) parts.add(count(tok) + " tok");
        if (n(s.subagent())) parts.add("subagent");
        var m = Snippets.matchLabel(h);
        if (!m.isEmpty()) parts.add("matched " + m);
        return String.join("   ·   ", parts);
    }
}
