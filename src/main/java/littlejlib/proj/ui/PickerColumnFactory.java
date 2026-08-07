package littlejlib.proj.ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import littlejlib.proj.model.ProjectRecord;

import java.util.Comparator;
import java.util.function.Function;

/**
 * How a picker column is built, as opposed to which columns there are.
 * <p>
 * Each factory stamps the column with its {@link PickerColumn} id, which is what lets order and
 * visibility be remembered across runs - a column with no id cannot be recognised again.
 */
public final class PickerColumnFactory {

    private PickerColumnFactory() { }

    /** A plain text column with no identity, for the sub-columns nested under a group header. */
    static TreeTableColumn<ProjectRecord, String> text(String title, double width,
                                                       Function<ProjectRecord, String> get) {
        var col = new TreeTableColumn<ProjectRecord, String>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(p -> {
            var rec = p.getValue() == null ? null : p.getValue().getValue();
            var v = rec == null ? "" : get.apply(rec);
            return new SimpleStringProperty(v == null ? "" : v);
        });
        return col;
    }

    static TreeTableColumn<ProjectRecord, String> text(PickerColumn which, double width,
                                                       Function<ProjectRecord, String> get) {
        var col = text(which.title, width, get);
        col.setId(which.id);
        return col;
    }

    /**
     * A text column with a tooltip. Kept apart from the plain one because a cell factory is not
     * free: this builds a Tooltip per painted cell, which is worth it for the columns carrying more
     * than they can show and wasteful for the rest.
     */
    static TreeTableColumn<ProjectRecord, String> text(PickerColumn which, double width,
                                                       Function<ProjectRecord, String> get,
                                                       Function<ProjectRecord, String> tooltip) {
        var col = text(which, width, get);
        col.setCellFactory(c -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(value);
                var row = getTableRow();
                var rec = row == null ? null : row.getItem();
                var tip = rec == null ? null : tooltip.apply(rec);
                setTooltip(tip == null || tip.isBlank() ? null : new Tooltip(tip));
            }
        });
        return col;
    }

    /** A column with no value, whose cell is entirely built by its own factory - buttons, icons. */
    static TreeTableColumn<ProjectRecord, String> widgets(PickerColumn which, double width) {
        var col = new TreeTableColumn<ProjectRecord, String>(which.title);
        col.setId(which.id);
        col.setPrefWidth(width);
        col.setSortable(false);   // sorting a column of buttons means nothing
        col.setCellValueFactory(p -> new SimpleStringProperty(""));
        return col;
    }

    /**
     * A column whose value is the record, so its sort order can be anything about the project rather
     * than the alphabetical order of whatever the cell happens to display.
     */
    static TreeTableColumn<ProjectRecord, ProjectRecord> record(
            PickerColumn which, double width, Function<ProjectRecord, String> render,
            Function<ProjectRecord, String> tooltip, Comparator<ProjectRecord> order) {

        var col = new TreeTableColumn<ProjectRecord, ProjectRecord>(which.title);
        col.setId(which.id);
        col.setPrefWidth(width);
        col.setCellValueFactory(p -> new SimpleObjectProperty<>(
                p.getValue() == null ? null : p.getValue().getValue()));
        col.setComparator(nullsLast(order));
        col.setCellFactory(c -> new TreeTableCell<>() {
            @Override
            protected void updateItem(ProjectRecord rec, boolean empty) {
                super.updateItem(rec, empty);
                if (empty || rec == null || rec.isCodeDirNode()) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(render.apply(rec));
                var tip = tooltip.apply(rec);
                setTooltip(tip == null || tip.isBlank() ? null : new Tooltip(tip));
            }
        });
        return col;
    }

    /** The synthetic code-directory rows carry no value at all; they must not lead the sort. */
    static Comparator<ProjectRecord> nullsLast(Comparator<ProjectRecord> order) {
        return (a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            return order.compare(a, b);
        };
    }
}
