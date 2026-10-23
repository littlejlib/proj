package littlejlib.proj.ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.*;
import littlejlib.proj.cct.export.Stamp;
import littlejlib.proj.hib.HibRecord;
import littlejlib.proj.hib.LiveSession;
import littlejlib.proj.hib.ProcMem;

import java.util.function.Function;

/** The two tables of the Conversations tab: running now, and hibernated. */
public final class ConversationTables {

    private ConversationTables() { }

    public static TableView<LiveSession> running() {
        var t = new TableView<LiveSession>();
        t.getColumns().add(text("Name", 240, LiveSession::label));
        t.getColumns().add(text("Status", 70, LiveSession::status));
        t.getColumns().add(number("Idle min", 70, s -> s.idleMinutes(System.currentTimeMillis())));
        t.getColumns().add(number("RAM MB", 70, s -> mb(s.workingSet())));
        t.getColumns().add(number("Committed MB", 95, s -> mb(s.privateBytes())));
        t.getColumns().add(number("Turns", 55, s -> s.humanTurns() == null ? -1 : s.humanTurns()));
        t.getColumns().add(text("PRP", 45, s -> s.prpIndex() == null ? "…" : s.prpIndex()));
        t.getColumns().add(text("Folder", 420, LiveSession::cwd));
        t.setPlaceholder(new Label("no claude-code conversation is running"));
        t.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        return t;
    }

    public static TableView<HibRecord> parked() {
        var t = new TableView<HibRecord>();
        t.getColumns().add(text("", 40, r -> r.hibernated() ? "zz" : "was"));
        t.getColumns().add(text("Hibernated", 130, r -> r.hibernatedAt() == null ? "" : Stamp.of(r.hibernatedAt())));
        t.getColumns().add(text("Name", 240, HibRecord::label));
        t.getColumns().add(text("PRP", 260, HibRecord::prpLabel));
        t.getColumns().add(number("Turns", 55, r -> r.humanTurns() == null ? -1 : r.humanTurns()));
        t.getColumns().add(number("RAM MB", 70, r -> mb(r.workingSet())));
        t.getColumns().add(text("Restored", 130, r -> r.restoredAt() == null ? "" : Stamp.of(r.restoredAt())));
        t.getColumns().add(text("Folder", 420, HibRecord::cwd));
        t.setPlaceholder(new Label("nothing hibernated in this view - F6 changes the view"));
        t.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        return t;
    }

    static <T> TableColumn<T, String> text(String title, double width, Function<T, String> value) {
        var c = new TableColumn<T, String>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(nz(value.apply(d.getValue()))));
        return c;
    }

    /** Numbers sort as numbers; -1 means unknown and shows blank. */
    static <T> TableColumn<T, Long> number(String title, double width, Function<T, Number> value) {
        var c = new TableColumn<T, Long>(title);
        c.setPrefWidth(width);
        c.setStyle("-fx-alignment: CENTER-RIGHT;");
        c.setCellValueFactory(d -> new SimpleObjectProperty<>(value.apply(d.getValue()).longValue()));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null || v < 0 ? "" : v.toString());
            }
        });
        return c;
    }

    static long mb(Long bytes) {
        return bytes == null ? -1 : Long.parseLong(ProcMem.mb(bytes));
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
