package littlejlib.proj.ui;

import javafx.scene.control.TreeTableRow;
import littlejlib.proj.model.ProjectRecord;

/**
 * A project row tinted by how recently it was worked on. See {@link Heat}.
 * <p>
 * Activity is the newer of two things, because neither alone is the answer. {@code lastOpened} is
 * this tool's own record of picking the project and is exactly right when it exists - and it does
 * not exist for a project you work in every day through an IDE and have never opened from here.
 * {@code lastModified} covers that case and is noisier. The newer of the two is warm when either
 * says so, which is the behaviour wanted: a false cold reading hides work in progress, a slightly
 * generous warm one costs nothing.
 * <p>
 * The synthetic code-directory rows are left unpainted. They are scaffolding rather than results,
 * and a heading with a temperature would be answering a question nobody asked of it.
 */
public class ProjectHeatRow extends TreeTableRow<ProjectRecord> {

    public ProjectHeatRow() {
        // Rows are recycled and re-selected without ever being re-bound to a new item, and the
        // selected colour is written inline (see Heat.paint), so the paint has to follow the
        // selection as well as the item - or the row you arrow off keeps its dark colour.
        selectedProperty().addListener((o, a, b) -> repaint());
    }

    @Override
    protected void updateItem(ProjectRecord rec, boolean empty) {
        super.updateItem(rec, empty);
        repaint();
    }

    void repaint() {
        var rec = getItem();
        var cold = rec == null || isEmpty() || rec.isCodeDirNode();
        Heat.paint(this, cold ? -1 : Heat.of(Heat.newest(rec.lastOpened(), rec.lastModified())));
    }
}
