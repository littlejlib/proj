package littlejlib.proj.cct.ui;

import module java.base;

import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.*;

import static luvjfx.Fx.*;

public final class HelpOverlay {

    private HelpOverlay() {}

    private static Stage open;

    private static final List<String> KEYS = List.of(
        "type", "search - every letter and digit goes to the box, never to an action",
        "Enter / Down", "leave the box and move into the results",
        "Up / Down", "move the selection",
        "Esc", "back out: find bar to turns, turns to results, results to an empty box",
        "F11", "every action on one menu, each with its key beside it",
        "F1", "this list",
        "", "",
        "F2", "read the session as its turns",
        "Alt+F", "find inside the open conversation - Enter next, Shift+Enter previous",
        "Alt+A", "select every turn",
        "F12", "copy the selected turns - one, or all of them",
        "Alt+T", "copy the whole conversation as text (clipboard only)",
        "", "",
        "F3", "copy the resume command, in the shell shown on the chip",
        "Alt+H", "change that shell: cmd, cmd&&, powershell, bash",
        "Alt+B", "copy a block: title, id, folder and the command in every shell",
        "F4", "copy the recorded folder path",
        "F8", "copy the session id",
        "", "",
        "Alt+E", "export this conversation as markdown",
        "Alt+X", "export as semantic xhtml; the F11 menu also has text and raw jsonl",
        "", "",
        "Alt+P", "open the PRP file this session opened with",
        "F7", "reveal the folder in Explorer",
        "F6", "open a terminal in the recorded folder",
        "Alt+J", "show this project in the Projects tab (only inside proj)",
        "", "",
        "F9", "widen the scope: your turns, then Claude's replies too",
        "F10", "match mode: auto, substring, phrase, keywords",
        "F5", "reindex - incremental, only what changed is read",
        "Alt+S", "jump to the scratch space",
        "Alt+K", "clear the scratch space",
        "", "",
        "note", "everything you copy is also appended to the scratch space",
        "note", "sessions marked \"prompts only\" had their transcript pruned by claude-code",
        "note", "there is no thinking scope: claude-code stores thinking text as empty, keeping only the signature");

    public static void show() {
        if (open != null && open.isShowing()) { open.requestFocus(); return; }
        var grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(5);
        for (var i = 0; i < KEYS.size(); i += 2) {
            var k = new Label(KEYS.get(i));
            k.getStyleClass().add("warn-note");
            k.setMnemonicParsing(false);
            var v = new Label(KEYS.get(i + 1));
            v.getStyleClass().add("legend-label");
            v.setMnemonicParsing(false);
            grid.addRow(i / 2, k, v);
        }
        var head = new Label("Transcripts - keys");
        head.getStyleClass().add("hit-title");
        head.setMnemonicParsing(false);
        var box = vbox().spacing(10).padding(new Insets(16))
            .attr(v -> v.getChildren().addAll(head, grid)).node;
        var scene = new Scene(box);
        Theme.applyLight(scene);
        Keys.disableMnemonics(box);
        var stage = new Stage(StageStyle.UTILITY);
        stage.setTitle("transcripts keys");
        stage.initModality(Modality.NONE);
        stage.setScene(scene);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.F1) stage.close();
        });
        stage.getIcons().addAll(AppIcon.sizes());
        open = stage;
        stage.show();
    }
}
