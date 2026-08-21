package littlejlib.proj.cct.ui;

import module java.base;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import littlejlib.proj.cct.store.StorePaths;

import static luvjfx.Fx.*;

public final class ScratchPad {

    private final TextArea area = new TextArea();
    private final VBox root;

    public ScratchPad() {
        area.getStyleClass().add("text-area");
        area.setWrapText(false);
        area.setPromptText("scratch space — everything you copy lands here; edit freely, then paste into a terminal or into claude");
        VBox.setVgrow(area, Priority.ALWAYS);
        area.setMinWidth(0);
        area.setMinHeight(0);
        area.setPrefColumnCount(1);
        var head = new Label("Scratch   ·   Alt+S jump here   ·   Alt+K clear   ·   saved automatically");
        head.getStyleClass().add("section-head");
        head.setMnemonicParsing(false);
        root = vbox().padding(new Insets(4, 12, 8, 12)).spacing(2)
            .attr(v -> v.getChildren().addAll(head, area)).node;
        root.setMinWidth(0);
        head.setMinWidth(0);
        area.setText(read());
        area.textProperty().addListener((o, a, b) -> write(b));
    }

    public Node node() { return root; }

    public TextArea area() { return area; }

    public void append(String text) {
        if (text == null || text.isBlank()) return;
        var current = area.getText();
        var sep = current.isEmpty() ? "" : (current.endsWith("\n") ? "" : "\n");
        area.setText(current + sep + text + "\n");
        area.positionCaret(area.getText().length());
    }

    public void clear() { area.clear(); }

    public void focus() {
        area.requestFocus();
        area.positionCaret(area.getText().length());
    }

    private static Path file() { return StorePaths.scratchFile(); }

    private static String read() {
        try {
            var f = file();
            return Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void write(String text) {
        try {
            StorePaths.ensureAppHome();
            Files.writeString(file(), text == null ? "" : text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // the scratch pad is a convenience; a failed save must not break the window
        }
    }
}
