package littlejlib.proj.ui;

import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import littlejlib.proj.prp.PrpSessions;
import littlejlib.proj.prp.TabName;

import java.nio.file.Path;

/**
 * Setting a project's key - the {@code billing} that makes its PRP 38 read {@code billing-38}.
 * <p>
 * It could already be set by typing a tab name while starting an agent, which is fine as a side
 * effect and useless as the only way in: it means the name of a project cannot be decided until
 * something is launched from it.
 */
public final class ProjectKeyDialog {

    private ProjectKeyDialog() { }

    public static void show(Stage owner, Path project, Runnable afterChange) {
        if (project == null) {
            Toast.show("select a project or a PRP first");
            return;
        }
        var dialog = build(project);
        if (owner != null) dialog.initOwner(owner);

        dialog.showAndWait().ifPresent(typed -> {
            var clean = TabName.cleanKey(typed);
            if (clean.isEmpty()) {
                Toast.show("a key needs at least one letter or digit");
                return;
            }
            PrpSessions.setKey(project, clean);
            Toast.show("PRPs of this project now read " + clean + "-NN");
            if (afterChange != null) afterChange.run();
        });
    }

    /** Built without being shown, so it can be photographed offscreen. */
    public static TextInputDialog build(Path project) {
        var current = PrpSessions.key(project);
        var seeded = current == null ? TabName.shortenProject(project) : current;
        var example = exampleIndex(project);

        var dialog = new TextInputDialog(seeded);
        AppIcon.on(dialog);
        dialog.setTitle("Project key");
        dialog.setHeaderText((current == null
                ? "No key set for this project yet, so this is a guess from the folder name."
                : "The key set for this project.")
                + "\nPRP " + example + " would read " + seeded + "-" + example
                + ", and its terminal tab " + seeded + "-" + example + "-<task>."
                + "\n\n" + project);
        dialog.setContentText("Key");
        return dialog;
    }

    /** A real PRP of this project, so the preview shows a number the user recognises. */
    static String exampleIndex(Path project) {
        try {
            var prps = littlejlib.proj.prp.PrpCreator.list(
                    littlejlib.proj.prp.PrpCreator.prpDirOf(project));
            return prps.stream().map(littlejlib.proj.prp.PrpEntry::getIndex)
                    .max(String::compareTo).orElse("38");
        } catch (Exception e) {
            return "38";
        }
    }
}
