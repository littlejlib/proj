package littlejlib.proj.cct.ui;

import module java.base;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import littlejlib.proj.cct.model.TurnDoc;
import littlejlib.proj.cct.store.*;

import static littlejlib.proj.cct.Num.*;

public final class TurnCell extends ListCell<TurnDoc> {

    private final Label meta = new Label();
    private final Label body = new Label();
    private final VBox box = new VBox(2, meta, body);

    public TurnCell(ListView<TurnDoc> owner) {
        meta.getStyleClass().add("turn-meta");
        meta.setMnemonicParsing(false);
        body.setWrapText(true);
        body.setMnemonicParsing(false);
        body.maxWidthProperty().bind(owner.widthProperty().subtract(46));
        setText(null);
    }

    @Override
    protected void updateItem(TurnDoc t, boolean empty) {
        super.updateItem(t, empty);
        if (empty || t == null) { setGraphic(null); return; }
        var layer = Layer.ofDepth(n(t.layer()));
        body.getStyleClass().setAll(layer == Layer.HUMAN ? "turn-human" : "turn-body");
        meta.setText(head(t, layer));
        body.setText(text(t, layer));
        setGraphic(box);
    }

    private static String head(TurnDoc t, Layer layer) {
        var parts = new ArrayList<String>();
        parts.add(layer == Layer.HUMAN ? "YOU" : layer.label.toUpperCase(Locale.ROOT));
        parts.add(Timestamps.local(n(t.timestamp())));
        if (t.model() != null) parts.add(t.model());
        if (t.effort() != null) parts.add("effort " + t.effort());
        if (n(t.sidechain())) parts.add("sidechain" + (t.agentId() == null ? "" : " " + t.agentId()));
        return String.join("   ·   ", parts);
    }

    private static String text(TurnDoc t, Layer layer) {
        var s = t.text() == null ? "" : t.text();
        if (layer == Layer.HUMAN) return s;
        return s.length() <= 1200 ? s : s.substring(0, 1200) + "  …";
    }
}
