package littlejlib.proj.ui;

import javafx.scene.control.*;
import javafx.stage.Stage;
import littlejlib.proj.prp.ProjNames;

import java.nio.file.Path;

/**
 * A project's {@code prp/proj.json}, edited without opening the file: the short name that makes PRP
 * 38 read {@code billing-38}, a long name, and the icon its terminal tab carries.
 * <p>
 * Opening it writes the file if there is none, which is where a new project's values get inferred.
 */
public final class ProjectDialog {

    private ProjectDialog() { }

    public static void show(Stage owner, Path project, Runnable afterChange) {
        if (project == null) {
            Toast.show("select a project or a PRP first");
            return;
        }
        var form = new ProjectForm(project);
        var dialog = form.dialog();
        if (owner != null) dialog.initOwner(owner);
        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> form.save(afterChange));
    }

    /**
     * The gate of a project's first PRP: when it has no proj.json, asks for the four names and writes
     * the file. False when the user cancelled, so no PRP should be made.
     */
    public static boolean requireNames(Stage owner, Path project) {
        if (project == null || !ProjNames.missing(project)) return true;
        var form = new ProjectForm(project, true);
        var dialog = form.dialog();
        if (owner != null) dialog.initOwner(owner);
        var ok = dialog.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
        if (ok) form.save(null);
        return ok;
    }

    /** Built without being shown, so it can be photographed offscreen. */
    public static Dialog<ButtonType> build(Path project) {
        return new ProjectForm(project).dialog();
    }
}
