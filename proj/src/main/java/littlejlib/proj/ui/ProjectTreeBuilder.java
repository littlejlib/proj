package littlejlib.proj.ui;

import javafx.scene.control.TreeItem;
import littlejlib.proj.model.ProjectRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Rebuilds the hierarchy from flat rows using parentPath, into the TreeItems the view wants.
 * <p>
 * A row is kept when it matches the filter or when any descendant does, so filtering to a leaf
 * still shows the path down to it. Siblings are ordered by the chosen {@link ProjectSort} at every
 * level, so the ordering never fights the hierarchy. When more than one code directory is being
 * browsed, each gets a synthetic grouping row - otherwise two roots called "examples" from
 * different code directories would be indistinguishable.
 */
public final class ProjectTreeBuilder {

    private ProjectTreeBuilder() { }

    public static TreeItem<ProjectRecord> build(List<ProjectRecord> rows, Predicate<ProjectRecord> keep,
                                                java.util.Comparator<ProjectRecord> order, boolean groupByCodeDir) {
        var root = new TreeItem<ProjectRecord>(null);
        root.setExpanded(true);

        if (!groupByCodeDir) {
            root.getChildren().setAll(subtree(rows, keep, order));
            return root;
        }

        var byCodeDir = new LinkedHashMap<String, List<ProjectRecord>>();
        for (var r : rows) byCodeDir.computeIfAbsent(r.codeDir(), k -> new ArrayList<>()).add(r);

        for (var entry : byCodeDir.entrySet()) {
            var kids = subtree(entry.getValue(), keep, order);
            if (kids.isEmpty()) continue;
            var group = new TreeItem<>(new ProjectRecord().path(entry.getKey()).name(entry.getKey())
                    .codeDir(entry.getKey()).relativePath(""));
            group.setExpanded(true);
            group.getChildren().setAll(kids);
            root.getChildren().add(group);
        }
        return root;
    }

    static List<TreeItem<ProjectRecord>> subtree(List<ProjectRecord> rows, Predicate<ProjectRecord> keep,
                                                 java.util.Comparator<ProjectRecord> order) {
        var byPath = new HashMap<String, ProjectRecord>();
        for (var r : rows) byPath.put(r.path(), r);

        var childrenOf = new HashMap<String, List<ProjectRecord>>();
        var roots = new ArrayList<ProjectRecord>();
        for (var r : rows) {
            var parent = r.parentPath();
            if (parent == null || parent.isBlank() || !byPath.containsKey(parent)) roots.add(r);
            else childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(r);
        }

        roots.sort(order);
        var out = new ArrayList<TreeItem<ProjectRecord>>();
        for (var r : roots) {
            var item = toItem(r, childrenOf, keep, order);
            if (item != null) out.add(item);
        }
        return out;
    }

    static TreeItem<ProjectRecord> toItem(ProjectRecord rec, Map<String, List<ProjectRecord>> childrenOf,
                                          Predicate<ProjectRecord> keep, java.util.Comparator<ProjectRecord> order) {
        var siblings = new ArrayList<>(childrenOf.getOrDefault(rec.path(), List.of()));
        siblings.sort(order);

        var kids = new ArrayList<TreeItem<ProjectRecord>>();
        for (var child : siblings) {
            var item = toItem(child, childrenOf, keep, order);
            if (item != null) kids.add(item);
        }

        if (kids.isEmpty() && !keep.test(rec)) return null;

        var item = new TreeItem<>(rec);
        item.setExpanded(true);
        item.getChildren().setAll(kids);
        return item;
    }
}
