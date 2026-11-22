package littlejlib.proj.cct.ui;

import module java.base;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import littlejlib.proj.cct.action.ShellKind;
import littlejlib.proj.cct.search.MatchMode;

import static luvjfx.Fx.*;

public final class Chips {

    private final Label layerChip = chip(), modeChip = chip(), shellChip = chip();
    private final HBox bar;

    public Chips() {
        bar = hbox().spacing(6).alignment(Pos.CENTER_LEFT)
            .attr(h -> h.getChildren().addAll(layerChip, modeChip, shellChip)).node;
    }

    private static Label chip() {
        var l = new Label();
        l.getStyleClass().add("chip");
        l.setMnemonicParsing(false);
        return l;
    }

    public Node node() { return bar; }

    public void set(int layer, MatchMode mode, ShellKind shell) {
        layerChip.setText("F9  scope: " + scope(layer));
        layerChip.getStyleClass().setAll("chip", layer > 1 ? "chip-on" : "chip");
        modeChip.setText("F10  match: " + mode.name().toLowerCase(Locale.ROOT));
        modeChip.getStyleClass().setAll("chip", mode == MatchMode.AUTO ? "chip" : "chip-on");
        shellChip.setText("Alt+H  shell: " + shell.label);
        shellChip.getStyleClass().setAll("chip", shell == ShellKind.preferred() ? "chip" : "chip-on");
    }

    /**
     * Three scopes, and the third is not thinking. claude-code stores {@code "thinking":""} for every
     * thinking block on this machine - 16,642 of them, text redacted, only the encrypted signature
     * kept - so a thinking scope could never return anything. The third is the TOOL CALLS, which the
     * index does not hold (layer 4 is most of the store's 825 MB) and which are therefore read
     * straight from the session file when asked for.
     */
    static String scope(int layer) {
        return switch (layer) {
            case 2 -> "your turns + Claude's replies";
            case 3 -> "everything, tool calls included (read from file)";
            default -> "your turns only";
        };
    }
}
