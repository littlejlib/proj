package littlejlib.proj;

import littlejlib.proj.ui.ProjectPickerDialog;

import java.nio.file.Path;

/**
 * Opens the merged picker against the real settings, so it can actually be looked at.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SmokePicker" -Dexec.classpathScope=test
 */
public class SmokePicker {

    public static void main(String[] args) {
        var settings = new SettingsManager().loadSettings();
        if (settings == null) {
            System.err.println("no settings found");
            System.exit(1);
        }
        var paths = args.length > 0
                ? java.util.List.of(Path.of(args[0]))
                : settings.getBrowsePaths().stream().map(Path::of)
                        .filter(java.nio.file.Files::isDirectory).toList();
        System.out.println("picker over " + paths);

        var result = ProjectPickerDialog.show(paths, settings);
        System.out.println("result = " + (result == null ? "cancelled"
                : result.projectPath() + " -> " + result.absolutePath()));
        System.exit(0);
    }
}
