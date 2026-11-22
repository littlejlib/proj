package littlejlib.proj.ui;

import littlejlib.proj.Clipboard;
import littlejlib.proj.Settings;

import java.nio.file.Path;
import java.util.List;

/**
 * What can be done with a folder of a PLAIN group.
 */
public final class WorkspaceActions {

    /** In legend order. Anything outside this set is not offered rather than offered and inert. */
    public static final List<ProjectAction> SUPPORTED = List.of(
            ProjectAction.TERMINAL_CD,
            ProjectAction.OPEN_FOLDER,
            ProjectAction.OPEN_CONSOLE,
            ProjectAction.COPY_PATH,
            ProjectAction.PRP);

    private WorkspaceActions() { }

    public static boolean supports(ProjectAction action) {
        return SUPPORTED.contains(action);
    }

    public static ProjectAction byKey(int fKey) {
        return SUPPORTED.stream().filter(a -> a.fKey == fKey).findFirst().orElse(null);
    }

    /** Everything except TERMINAL_CD and PRP, which the pane has to handle - they change the window. */
    public static void perform(ProjectAction action, Path dir, Settings settings) {
        if (action == null || dir == null) return;
        switch (action) {
            case OPEN_FOLDER -> ProjectActions.openFolder(dir);
            case OPEN_CONSOLE -> ProjectActions.openConsole(dir, settings);
            case COPY_PATH -> {
                if (Clipboard.copy(dir.toAbsolutePath().toString())) Toast.show("Path copied");
            }
            default -> { }
        }
    }
}
