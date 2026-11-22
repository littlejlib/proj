package littlejlib.proj.cct.ui;

import module java.base;

import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/**
 * The permanent legend, in the coloured chips proj's project table uses.
 * <p>
 * It was flat text with every key in the accent colour, which made a whole row of orange at the
 * bottom of the window and gave no clue which keys belonged together. proj had solved this already:
 * one tint per KIND of action, so the legend is read once and every menu and button is recognised by
 * colour from then on. Same tints here, same meanings - navigation blue, filesystem amber, clipboard
 * purple, index teal, export green, view grey.
 */
public final class StatusBar {

    private final Label message = new Label("");
    private final VBox bar;

    public StatusBar() {
        var legend = new FlowPane(Orientation.HORIZONTAL, 4, 3);
        legend.setMinWidth(0);
        legend.setPrefWrapLength(0);
        legend.getChildren().addAll(chips());
        message.getStyleClass().add("legend-label");
        message.setMinWidth(0);
        message.setWrapText(true);
        bar = new VBox(3, legend, message);
        bar.getStyleClass().add("status-bar");
        bar.setMinWidth(0);
        bar.setMinHeight(0);
        bar.setPadding(new Insets(5, 10, 5, 10));
    }

    private List<Node> chips() {
        var out = new ArrayList<Node>();
        out.add(chip("F11 all actions", littlejlib.proj.ui.Palette.GREY));
        out.add(chip("F1 all keys", littlejlib.proj.ui.Palette.GREY));
        out.add(chip("F2 turns", littlejlib.proj.ui.Palette.BLUE));
        out.add(chip("Alt+F find here", littlejlib.proj.ui.Palette.BLUE));
        out.add(chip("Alt+A all turns", littlejlib.proj.ui.Palette.BLUE));
        out.add(chip("F3 resume cmd", littlejlib.proj.ui.Palette.PURPLE));
        out.add(chip("F12 copy turns", littlejlib.proj.ui.Palette.PURPLE));
        out.add(chip("F4 path", littlejlib.proj.ui.Palette.PURPLE));
        out.add(chip("F8 id", littlejlib.proj.ui.Palette.PURPLE));
        out.add(chip("Alt+E export", littlejlib.proj.ui.Palette.GREEN));
        out.add(chip("F7 explorer", littlejlib.proj.ui.Palette.AMBER));
        out.add(chip("F6 terminal", littlejlib.proj.ui.Palette.AMBER));
        out.add(chip("Alt+P open PRP", littlejlib.proj.ui.Palette.PINK));
        out.add(chip("Alt+J in projects", littlejlib.proj.ui.Palette.PINK));
        out.add(chip("F9 scope", littlejlib.proj.ui.Palette.GREY));
        out.add(chip("F10 match", littlejlib.proj.ui.Palette.GREY));
        out.add(chip("F5 reindex", littlejlib.proj.ui.Palette.TEAL));
        out.add(chip("Esc back", littlejlib.proj.ui.Palette.GREY));
        return out;
    }

    private static Label chip(String text, String style) {
        var l = littlejlib.proj.ui.Chip.of(text, style);
        l.setMnemonicParsing(false);
        return l;
    }

    public Node node() { return bar; }

    public void say(String text) { message.setText(text == null ? "" : text); }
}
