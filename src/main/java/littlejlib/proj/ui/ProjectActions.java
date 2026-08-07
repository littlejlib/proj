package littlejlib.proj.ui;

import javafx.scene.control.Alert;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import littlejlib.proj.Settings;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.util.Cmd;

import java.nio.file.Path;

/**
 * Carries out a {@link ProjectAction} on a row. TERMINAL_CD and PIN_JACLI are not here - the first
 * has to close the dialog so the client can write the postrun script, the second belongs to the
 * jacli integration.
 */
public final class ProjectActions {

    private ProjectActions() { }

    static final String OS = System.getProperty("os.name", "").toLowerCase();
    static final boolean WINDOWS = OS.contains("win"), MAC = OS.contains("mac");

    public static void perform(ProjectAction action, ProjectRecord rec, Settings settings) {
        if (rec == null || !action.supportedBy(rec)) return;
        var dir = rec.dir();
        if (dir == null) return;

        switch (action) {
            case OPEN_FOLDER -> openFolder(dir);
            case OPEN_CONSOLE -> openConsole(dir, settings);
            case EDIT_POM -> editPom(rec, settings);
            case COPY_PATH -> copy(dir.toAbsolutePath().toString(), "Path copied");
            case COPY_DEPENDENCY -> copy(rec.dependencyXml(), "Dependency copied");
            case COPY_PROJECT -> ProjectReplicator.promptAndReplicate(rec);
            default -> { }
        }
    }

    public static void openFolder(Path dir) {
        if (WINDOWS) Cmd.run(new String[]{"explorer.exe", dir.toAbsolutePath().toString()}, null);
        else if (MAC) Cmd.run(new String[]{"open", dir.toAbsolutePath().toString()}, null);
        else Cmd.run(new String[]{"xdg-open", dir.toAbsolutePath().toString()}, null);
    }

    /**
     * Opens the user's terminal, not cmd.exe. Somebody who installed Tabby did it to stop being
     * given a Command Prompt, and hardcoding one here overruled that on every single row.
     */
    public static void openConsole(Path dir, Settings settings) {
        if (!littlejlib.proj.terminal.TerminalRunner.open(dir, settings)) {
            warn("Could not open a terminal", "Register one in Settings, or check the command template.");
        }
    }

    public static void launch(ProjectRecord rec, String commandTemplate) {
        var dir = rec == null ? null : rec.dir();
        if (dir == null) return;
        if (!Cmd.runTemplate(commandTemplate, dir)) warn("Could not launch", commandTemplate);
    }

    static void editPom(ProjectRecord rec, Settings settings) {
        var pom = rec.dir().resolve("pom.xml");
        var editor = settings == null ? null : settings.getSingleFileEditor();
        if (editor == null || editor.isBlank()) {
            // Nothing configured, so use whatever text editor this machine actually has.
            var detected = littlejlib.proj.ide.IdeDetector.detect().stream()
                    .filter(i -> !i.opensFolders).findFirst().orElse(null);
            if (detected == null) {
                warn("No editor found", "Set singleFileEditor in Settings.toml, or use proj settings.");
                return;
            }
            editor = detected.exe.toAbsolutePath().toString();
        }
        Cmd.run(new String[]{editor, pom.toAbsolutePath().toString()}, null);
    }

    static void copy(String text, String confirmation) {
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        Toast.show(confirmation);
    }

    static void warn(String header, String detail) {
        var a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Project tools");
        a.setHeaderText(header);
        a.setContentText(detail);
        a.showAndWait();
    }
}
