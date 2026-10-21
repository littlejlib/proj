package littlejlib.proj.ui;

import javafx.scene.control.TextInputDialog;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Makes an empty directory - the first gesture of a new project or a new case.
 */
public final class NewFolder {

    private NewFolder() { }

    /**
     * Asks for a name and creates it inside {@code parent}.
     *
     * @return the new directory, or null if the user cancelled or it could not be made
     */
    public static Path createUnder(Path parent) {
        if (parent == null) {
            Toast.show("select where the folder should go first");
            return null;
        }
        var dialog = new TextInputDialog();
        dialog.setTitle("New folder");
        dialog.setHeaderText("Create a folder in " + parent.getFileName());
        dialog.setContentText("Name");
        AppIcon.on(dialog);

        var answer = dialog.showAndWait();
        if (answer.isEmpty()) return null;
        var name = answer.get().trim();
        if (name.isEmpty()) return null;

        try {
            var target = parent.resolve(name);
            // Said rather than silently reused: "created" over a folder that was already there is a
            if (Files.isDirectory(target)) {
                Toast.show(name + " already exists");
                return target;
            }
            Files.createDirectories(target);
            Toast.show("created " + name);
            return target;
        } catch (InvalidPathException e) {
            Toast.show("not a usable folder name: " + name);
        } catch (Exception e) {
            Toast.show("could not create " + name + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Where a new folder belongs given what is selected: INSIDE the selected directory, since that
     * is what "new folder here" means everywhere else; falls back to the base when nothing is
     * selected, so the button still works on an empty list.
     */
    public static Path parentFor(Path selected, Path base) {
        if (selected != null && Files.isDirectory(selected)) return selected;
        return base;
    }
}
