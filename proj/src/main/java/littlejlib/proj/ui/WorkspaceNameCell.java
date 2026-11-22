package littlejlib.proj.ui;

import javafx.scene.control.TreeTableCell;
import littlejlib.proj.workspace.WorkspaceRecord;

/**
 * A folder's path or its name. Text styling only - the heat is painted on the ROW, by
 * {@link WorkspaceHeatRow}, because a cell's background is transparent in a TreeTableView.
 */
public final class WorkspaceNameCell extends TreeTableCell<WorkspaceRecord, String> {

    final boolean path;

    public WorkspaceNameCell(boolean path) {
        this.path = path;
    }

    @Override
    protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);
        var row = getTreeTableRow();
        var rec = empty || row == null || row.getTreeItem() == null ? null : row.getTreeItem().getValue();
        if (rec == null) {
            setText(null);
            setStyle("");
            return;
        }
        setText(value);
        // Never a background: that would paint over the row's heat, which is the colour being read.
        if (rec.isProject()) setStyle(path ? "-fx-text-fill: #37474F;" : "");
        else if (rec.isContainer()) setStyle("-fx-text-fill: #455A64; -fx-font-weight: bold;");
        else setStyle("-fx-text-fill: #9AA7AE; -fx-font-style: italic;");
    }
}
