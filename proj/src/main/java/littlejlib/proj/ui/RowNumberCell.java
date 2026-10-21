package littlejlib.proj.ui;

import javafx.scene.control.TreeTableCell;


/**
 * The visible row number. Independent of quick-select, which only reaches 1-9 - this numbers every
 * row so a long list stays readable and can be talked about.
 * <p>
 * Generic in the row type, because the folder list of a plain group wants the same numbering and
 * a second copy of four lines is four lines that can disagree.
 */
public class RowNumberCell<T> extends TreeTableCell<T, String> {

    public RowNumberCell() {
        setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: #90A4AE; -fx-font-size: 10px;");
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || getIndex() < 0 ? null : String.valueOf(getIndex() + 1));
    }
}
