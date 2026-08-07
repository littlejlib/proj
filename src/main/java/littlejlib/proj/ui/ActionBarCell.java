package littlejlib.proj.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.layout.HBox;
import littlejlib.proj.model.ProjectRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * All the per-row actions in one cell instead of one column each.
 * <p>
 * Seven action columns plus git plus description overflowed the window, and JavaFX has no frozen
 * columns to scroll the rest against - so the fix is to stop spending a column on each button.
 */
public class ActionBarCell extends TreeTableCell<ProjectRecord, String> {

    final HBox box = new HBox(2);
    final Map<ProjectAction, Button> buttons = new LinkedHashMap<>();

    public ActionBarCell(List<ProjectAction> actions, BiConsumer<ProjectAction, ProjectRecord> onAction) {
        box.setAlignment(Pos.CENTER_LEFT);
        for (var a : actions) {
            // The key is ON the button, not only in the legend at the bottom of the window. A
            // shortcut you have to look somewhere else to find is a shortcut you read the long way
            // round every time, which defeats the point of having one.
            var b = new Button(a.keyName() + " " + a.label);
            b.setFocusTraversable(false);
            b.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-background-radius: 3;" + a.style);
            b.setTooltip(new Tooltip(a.tooltip + "   (" + a.keyName() + ")"));
            b.setOnAction(e -> {
                var rec = current();
                if (rec != null) onAction.accept(a, rec);
            });
            buttons.put(a, b);
            box.getChildren().add(b);
        }
    }

    ProjectRecord current() {
        var row = getTreeTableRow();
        var item = row == null ? null : row.getTreeItem();
        return item == null ? null : item.getValue();
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        var rec = current();
        if (empty || rec == null) {
            setGraphic(null);
            return;
        }
        buttons.forEach((action, button) -> button.setDisable(!action.supportedBy(rec)));
        setGraphic(box);
    }
}
