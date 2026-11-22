package littlejlib.proj.ui;

import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.util.Duration;
import littlejlib.proj.Settings;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.model.ProjectRecord;

import java.util.*;

/**
 * Which picker columns are shown, in what order, and remembering the answer.
 * <p>
 * Both were already adjustable in the table itself - drag a header to move a column, use the menu
 * button at the top right of the header to hide one - and both were discarded on every close, which
 * is what made adjusting them not worth the trouble. Keyed by {@link PickerColumn} id rather than by
 * heading or position, so rewording a heading does not silently reset everyone's layout.
 */
public final class PickerColumnLayout {

    private PickerColumnLayout() { }

    /** Adds the columns in the remembered order, with the remembered visibility. */
    public static void apply(TreeTableView<ProjectRecord> table, Settings settings,
                             Map<PickerColumn, TreeTableColumn<ProjectRecord, ?>> built) {
        var hidden = Set.copyOf(settings.getPickerHiddenColumns());
        // "Not listed as hidden" only means visible once a layout HAS been saved. Before that the
        // hidden list is empty because nothing was ever recorded, not because everything is on.
        var customised = !settings.getPickerColumnOrder().isEmpty() || !hidden.isEmpty();

        for (var which : PickerColumn.order(settings.getPickerColumnOrder())) {
            var col = built.get(which);
            if (col == null) continue;   // the IDE column, on a machine where none was detected
            col.setVisible(customised ? !hidden.contains(which.id) : which.shownByDefault);
            table.getColumns().add(col);
        }
        retargetTreeColumn(table, built);
    }

    /**
     * Writes the layout back whenever the user changes it.
     * <p>
     * Debounced, because dragging a header across five columns is five list mutations and none of
     * the intermediate ones is a layout anybody meant to keep. Wired only after {@link #apply}, so
     * installing the columns is not itself mistaken for a change worth saving.
     */
    public static void remember(TreeTableView<ProjectRecord> table, Settings settings,
                                Map<PickerColumn, TreeTableColumn<ProjectRecord, ?>> built) {
        var debounce = new PauseTransition(Duration.millis(600));
        debounce.setOnFinished(e -> save(table, settings, built));

        built.values().forEach(col -> col.visibleProperty().addListener((o, was, is) -> {
            retargetTreeColumn(table, built);
            debounce.playFromStart();
        }));
        table.getColumns().addListener(
                (ListChangeListener<TreeTableColumn<ProjectRecord, ?>>) c -> debounce.playFromStart());
    }

    /**
     * The disclosure arrows live in the tree column, so it has to be one that is on screen. Project
     * is the natural home; if it has been hidden the leftmost visible column takes over, rather than
     * the tree quietly becoming unexpandable.
     */
    static void retargetTreeColumn(TreeTableView<ProjectRecord> table,
                                   Map<PickerColumn, TreeTableColumn<ProjectRecord, ?>> built) {
        var project = built.get(PickerColumn.PROJECT);
        if (project != null && project.isVisible()) {
            table.setTreeColumn(project);
            return;
        }
        table.getColumns().stream().filter(TreeTableColumn::isVisible).findFirst()
                .ifPresent(table::setTreeColumn);
    }

    static void save(TreeTableView<ProjectRecord> table, Settings settings,
                     Map<PickerColumn, TreeTableColumn<ProjectRecord, ?>> built) {
        var idOf = new IdentityHashMap<TreeTableColumn<?, ?>, PickerColumn>();
        built.forEach((which, col) -> idOf.put(col, which));

        var order = new ArrayList<String>();
        var hidden = new ArrayList<String>();
        for (var col : table.getColumns()) {
            var which = idOf.get(col);
            if (which == null) continue;
            order.add(which.id);
            if (!col.isVisible()) hidden.add(which.id);
        }
        if (order.isEmpty()) return;   // nothing recognisable on screen; do not record that as a layout

        settings.setPickerColumnOrder(order);
        settings.setPickerHiddenColumns(hidden);

        // Re-read before writing. This window outlives almost everything else in the daemon, and the
        // Settings object behind the settings dialog is a different one; writing this whole object
        // back would undo whatever had been changed there since the picker was built.
        try {
            var manager = new SettingsManager();
            var onDisk = manager.loadSettings();
            if (onDisk == null) return;
            onDisk.setPickerColumnOrder(order);
            onDisk.setPickerHiddenColumns(hidden);
            manager.saveSettings(onDisk);
        } catch (Exception e) {
            // A column layout is not worth failing a picker over.
            System.err.println("[PickerColumnLayout] could not save the layout: " + e.getMessage());
        }
    }
}
