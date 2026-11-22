package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import littlejlib.proj.Settings;
import littlejlib.proj.group.ProjectGroup;
import littlejlib.proj.workspace.WorkspaceRecord;
import littlejlib.proj.workspace.WorkspaceRefresh;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Refresh for a group whose bases are a network filesystem: the button, the clock and what the
 * walk is doing while it runs.
 * <p>
 * The stamp is here rather than in a tooltip because the whole tab is now refreshed by something
 * the user did not press. A list that updates on its own has to say when it last did, or it cannot
 * be told apart from one that has quietly stopped.
 */
public final class WorkspaceScanBar {

    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm"),
            DAY = DateTimeFormatter.ofPattern("d MMM HH:mm");

    public final HBox node;
    public final Button button = new Button("Refresh");

    final Label stamp = new Label();
    final ProgressBar bar = new ProgressBar();
    final ProjectGroup group;
    final Settings settings;
    final Consumer<List<WorkspaceRecord>> onRows;

    volatile boolean busy;

    public WorkspaceScanBar(ProjectGroup group, Settings settings, Consumer<List<WorkspaceRecord>> onRows) {
        this.group = group;
        this.settings = settings;
        this.onRows = onRows;

        button.setTooltip(new Tooltip("Re-walk " + group.displayLabel() + " now   (F12)"));
        button.setOnAction(e -> rescan());

        stamp.setStyle("-fx-font-size: 10px; -fx-text-fill: #78909C;");
        bar.setPrefWidth(80);
        bar.setPrefHeight(8);
        show(bar, false);

        node = new HBox(6, button, bar, stamp);
        node.setAlignment(Pos.CENTER_LEFT);
        showStamp();
    }

    /**
     * The clock half, checked whenever the tab is shown. Without this the automatic refresh would
     * only ever happen in the daemon, and a picker started without one would never refresh at all.
     */
    public void refreshIfDue() {
        if (busy) return;
        var minutes = WorkspaceRefresh.minutes(settings);
        if (minutes <= 0) return;
        // Off the FX thread for the same reason as showStamp: asking is a database read.
        new Thread(() -> {
            if (WorkspaceRefresh.due(group, minutes)) Platform.runLater(this::rescan);
        }, "Workspace-Due-" + group.slug()).start();
    }

    /** Forced: whatever the walk returns is written, however little of it there is. */
    public void rescan() {
        if (busy) {
            Toast.show("already rescanning " + group.displayLabel());
            return;
        }
        busy = true;
        button.setDisable(true);
        show(bar, true);
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        stamp.setText("scanning " + group.displayLabel() + " ...");

        new Thread(() -> {
            var t0 = System.currentTimeMillis();
            var rows = WorkspaceRefresh.run(group, true,
                    (folders, found) -> Platform.runLater(() ->
                            stamp.setText("scanning ... " + folders + " folders, " + found + " projects")),
                    note -> Platform.runLater(() -> Toast.show(note)));
            var ms = System.currentTimeMillis() - t0;
            Platform.runLater(() -> {
                busy = false;
                button.setDisable(false);
                show(bar, false);
                if (onRows != null) onRows.accept(rows);
                showStamp();
                Toast.show(rows.size() + " folders in " + group.displayLabel() + " in " + ms + " ms");
            });
        }, "Workspace-Rescan-" + group.slug()).start();
    }

    public boolean busy() {
        return busy;
    }

    /** Reloads from the index without walking anything - what an outside refresh lands on. */
    public void reloadStamp() {
        if (!busy) showStamp();
    }

    /**
     * The stamp costs a database read, and {@code DB.get()} OPENS the store in this process rather
     * than asking the daemon - so where it is not already open, doing that on the FX thread is
     * either a wait or a lock exception, during the layout of a pane. Where it IS open the read is
     * one indexed lookup and going off-thread would only make the stamp arrive late.
     */
    void showStamp() {
        if (littlejlib.proj.db.DB.isOpen()) {
            paintStamp(WorkspaceRefresh.lastScanned(group));
            return;
        }
        new Thread(() -> {
            var last = WorkspaceRefresh.lastScanned(group);
            Platform.runLater(() -> paintStamp(last));
        }, "Workspace-Stamp-" + group.slug()).start();
    }

    void paintStamp(long last) {
        if (busy) return;
        var every = WorkspaceRefresh.minutes(settings);
        var when = last < 0 ? "the index could not be read"
                : last == 0 ? "never scanned - press Refresh or F12" : "scanned " + when(last);
        stamp.setText(when + (every <= 0 ? "" : ", every " + every + " min"));
        stamp.setTooltip(new Tooltip(every <= 0
                ? "Automatic refresh is off (workspaceRefreshMinutes = 0)"
                : group.displayLabel() + " re-walks itself every " + every + " minutes, and whenever "
                        + "this tab is opened after that long"));
    }

    static String when(long millis) {
        var at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
        return at.toLocalDate().equals(java.time.LocalDate.now())
                ? CLOCK.format(at) : DAY.format(at);
    }

    static void show(javafx.scene.Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }
}
