package littlejlib.proj.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import littlejlib.proj.workspace.WorkspaceRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The columns of a PLAIN group's table.
 */
public final class WorkspaceColumns {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private WorkspaceColumns() { }

    public static void install(TreeTableView<WorkspaceRecord> table) {
        var pathCol = path();
        table.getColumns().setAll(number(), pathCol, name(), prps(), activity());
        // Without this the disclosure arrow and indentation land on whichever column is first
        // (Number, here), because that is TreeTableView's default when no tree column is set -
        // exactly the bug the projects table avoids with its own retargetTreeColumn. That put the
        // hierarchy in the "#" column instead of "Folder": row numbers grew a disclosure triangle
        // and child folders showed no indentation at all where a reader would expect it.
        table.setTreeColumn(pathCol);
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setShowRoot(false);
        table.setTableMenuButtonVisible(true);
        table.setRowFactory(t -> new WorkspaceHeatRow());
    }

    static TreeTableColumn<WorkspaceRecord, String> number() {
        var col = new TreeTableColumn<WorkspaceRecord, String>("#");
        col.setPrefWidth(46);
        col.setMaxWidth(60);
        col.setSortable(false);
        col.setCellValueFactory(c -> new SimpleStringProperty(""));
        col.setCellFactory(c -> new RowNumberCell<>());
        return col;
    }

    /** The TREE column, so the hierarchy hangs off the path - the same choice the projects
     * table makes with its Project column. */
    static TreeTableColumn<WorkspaceRecord, String> path() {
        var col = new TreeTableColumn<WorkspaceRecord, String>("Folder");
        col.setPrefWidth(430);
        col.setCellValueFactory(c -> new SimpleStringProperty(text(c, WorkspaceRecord::relativePath)));
        col.setCellFactory(c -> new WorkspaceNameCell(true));
        return col;
    }

    /** Just the leaf, because the path column truncates from the left in a deep tree. */
    static TreeTableColumn<WorkspaceRecord, String> name() {
        var col = new TreeTableColumn<WorkspaceRecord, String>("Name");
        col.setPrefWidth(240);
        col.setCellValueFactory(c -> new SimpleStringProperty(text(c, WorkspaceRecord::name)));
        col.setCellFactory(c -> new WorkspaceNameCell(false));
        return col;
    }

    static String text(TreeTableColumn.CellDataFeatures<WorkspaceRecord, String> c,
                       java.util.function.Function<WorkspaceRecord, String> of) {
        var r = c.getValue() == null ? null : c.getValue().getValue();
        var v = r == null ? null : of.apply(r);
        return v == null ? "" : v;
    }

    static TreeTableColumn<WorkspaceRecord, String> prps() {
        var col = new TreeTableColumn<WorkspaceRecord, String>("PRPs");
        col.setPrefWidth(70);
        col.setStyle("-fx-alignment: CENTER-RIGHT;");
        col.setCellValueFactory(c -> {
            var r = c.getValue() == null ? null : c.getValue().getValue();
            return new SimpleStringProperty(r == null || !r.hasPrp() ? "" : String.valueOf(r.prpCount()));
        });

        return col;
    }

    static TreeTableColumn<WorkspaceRecord, String> activity() {
        var col = new TreeTableColumn<WorkspaceRecord, String>("Last activity");
        col.setPrefWidth(120);
        col.setCellValueFactory(c -> {
            var r = c.getValue() == null ? null : c.getValue().getValue();
            return new SimpleStringProperty(r == null ? "" : day(r.activity()));
        });

        return col;
    }

    static String day(long millis) {
        return millis <= 0 ? "" : DAY.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
}
