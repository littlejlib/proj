package littlejlib.proj.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import littlejlib.proj.model.ProjectRecord;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A button in a tree-table cell, disabled on rows that do not support it - mm's OpenButton, with
 * the enablement rule handed in rather than switched on inside.
 */
public class ActionButtonCell extends TreeTableCell<ProjectRecord, String> {

    final Button button;
    final Predicate<ProjectRecord> enabled;

    public ActionButtonCell(String label, String tooltip, String style,
                            Predicate<ProjectRecord> enabled, Consumer<ProjectRecord> onClick) {
        this.enabled = enabled;
        this.button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(false);
        button.setStyle("-fx-font-size: 10px; -fx-padding: 1 4 1 4;" + (style == null ? "" : style));
        if (tooltip != null) button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> {
            var rec = current();
            if (rec != null) onClick.accept(rec);
        });
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
        button.setDisable(!enabled.test(rec));
        setGraphic(button);
    }
}
