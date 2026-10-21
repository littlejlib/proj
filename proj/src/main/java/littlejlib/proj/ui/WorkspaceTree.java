package littlejlib.proj.ui;

import javafx.scene.control.TreeItem;
import littlejlib.proj.workspace.WorkspaceRecord;

import java.util.HashMap;
import java.util.List;

/**
 * Rows into TreeItems, nested by {@code parentPath} or flat.
 */
public final class WorkspaceTree {

    private WorkspaceTree() { }

    public static void build(TreeItem<WorkspaceRecord> root, List<WorkspaceRecord> rows, boolean nested) {
        if (!nested) {
            rows.forEach(r -> root.getChildren().add(new TreeItem<>(r)));
            return;
        }
        var byPath = new HashMap<String, TreeItem<WorkspaceRecord>>();
        for (var r : rows) byPath.put(r.path(), new TreeItem<>(r));
        for (var r : rows) {
            var parent = byPath.get(r.parentPath());
            (parent == null ? root : parent).getChildren().add(byPath.get(r.path()));
        }
        root.getChildren().forEach(WorkspaceTree::expandWherePrps);
    }

    /** True when this node or anything below it carries PRPs; opens the branches that do. */
    static boolean expandWherePrps(TreeItem<WorkspaceRecord> item) {
        var below = false;
        for (var child : item.getChildren()) below |= expandWherePrps(child);
        var mine = item.getValue() != null && item.getValue().hasPrp();
        item.setExpanded(below);
        return below || mine;
    }
}
