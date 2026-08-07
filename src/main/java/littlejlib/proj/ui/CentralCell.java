package littlejlib.proj.ui;

import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import littlejlib.proj.central.CentralChecker;
import littlejlib.proj.model.CentralStatus;
import littlejlib.proj.model.ProjectRecord;

/**
 * The Maven Central column, which is where a viewport-lazy lookup is actually triggered.
 * <p>
 * Asking here is what makes it lazy for free: a TreeTableView only builds cells for rows near the
 * viewport, so scrolling is the thing that widens the query set. Nothing is looked up for the
 * hundreds of rows nobody has scrolled to, and an answer once obtained is cached in ArcadeDB.
 * <p>
 * This is not the whole story any more - a filter that asks "which projects are NOT on Central" is
 * asked of every row at once, including the ones nobody scrolled to, and is served by the whole-set
 * sweep instead. The two coexist: this keeps a browsing user's cells filling in as they scroll,
 * without waiting for a sweep to finish.
 */
public class CentralCell extends TreeTableCell<ProjectRecord, ProjectRecord> {

    final CentralChecker checker;
    final Runnable repaint;

    public CentralCell(CentralChecker checker, Runnable repaint) {
        this.checker = checker;
        this.repaint = repaint;
        setStyle("-fx-alignment: CENTER;");
    }

    @Override
    protected void updateItem(ProjectRecord rec, boolean empty) {
        super.updateItem(rec, empty);
        if (empty || rec == null || rec.isCodeDirNode() || !rec.maven()) {
            setText(null);
            setTooltip(null);
            return;
        }

        var coords = rec.coordinates();
        if (coords.isBlank()) {
            setText(null);
            setTooltip(new Tooltip("no resolvable coordinates, so there is nothing to look up"));
            return;
        }

        var status = checker.statusOf(coords);
        setText(status.glyph);
        setTooltip(new Tooltip(switch (status) {
            case PUBLISHED -> coords + " is on Maven Central";
            case ABSENT -> coords + " was not found on Maven Central";
            case CHECKING -> "checking " + coords;
            default -> "not looked up yet";
        }));

        if (status == CentralStatus.UNKNOWN) checker.request(coords, c -> repaint.run());
    }
}
