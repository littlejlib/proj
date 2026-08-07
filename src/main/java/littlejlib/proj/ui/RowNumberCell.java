package littlejlib.proj.ui;

import javafx.scene.control.TreeTableCell;
import littlejlib.proj.model.ProjectRecord;

/**
 * The visible row number. Independent of quick-select, which only reaches 1-9 - this numbers every
 * row so a long list stays readable and can be talked about.
 */
public class RowNumberCell extends TreeTableCell<ProjectRecord, String> {

    public RowNumberCell() {
        setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: #90A4AE; -fx-font-size: 10px;");
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || getIndex() < 0 ? null : String.valueOf(getIndex() + 1));
    }
}
