package littlejlib.proj.ui;

import javafx.scene.control.TreeTableRow;
import littlejlib.proj.workspace.WorkspaceRecord;

/**
 * A folder row tinted by how recently it was worked on - the counterpart of {@link ProjectHeatRow}.
 */
public class WorkspaceHeatRow extends TreeTableRow<WorkspaceRecord> {

    public WorkspaceHeatRow() {
        // Rows are recycled and re-selected without ever being re-bound to a new item, and the
        selectedProperty().addListener((o, a, b) -> repaint());
    }

    @Override
    protected void updateItem(WorkspaceRecord rec, boolean empty) {
        super.updateItem(rec, empty);
        repaint();
    }

    void repaint() {
        var rec = getItem();
        Heat.paint(this, rec == null || isEmpty() ? -1 : Heat.of(rec.lastActivity()));
    }
}
