package littlejlib.proj.daemon;

import littlejlib.proj.Settings;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.ui.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The main menu, served by the daemon, and whatever it leads to that can be done from here.
 * <p>
 * THE SPLIT, which is the whole design of this class. Everything the menu offers is either a window
 * or a terminal prompt. The windows are served here: JavaFX is already warm, and the picker
 * additionally needs the ArcadeDB directory lock this process holds - which is the bug that moved
 * the menu across in the first place, a menu shown in the client offering an option that then could
 * not open the database. The two items that prompt on a terminal, Git Clone and New Project, cannot
 * be served here because a daemon has no tty. Open in IDE joins them because the client already has
 * that code with its own console messages, and returning one string is cheaper than plumbing a
 * working directory through to launch it.
 * <p>
 * One menu per invocation rather than a loop, which is what the client did before this moved:
 * closing the PRP manager ends the command instead of returning to a menu already finished with.
 */
public final class MenuService {

    private MenuService() { }

    /**
     * @param note       how to tell the tray what is happening
     * @param openPicker the server's own picker call, which also records the chosen path
     */
    public static Response serve(Request request, Consumer<String> note, Supplier<Response> openPicker) {
        var settings = new SettingsManager().loadSettings();
        if (settings == null) return Response.error(3, "no settings; run proj once in a terminal to create them");

        note.accept("menu open");
        var action = MainMenuDialog.show(settings);
        note.accept("ready");

        return switch (action) {
            case OPEN_PROJECT -> openPicker.get();
            case PRPS_MANAGER -> {
                PrpManagerDialog.show(cwdOf(request).resolve("prp"), settings);
                yield Response.ok("");
            }
            case GLOBAL_PRPS_MANAGER -> {
                GlobalPrpManagerDialog.show(settings);
                yield Response.ok("");
            }
            case SETTINGS -> {
                showSettings(settings);
                yield Response.ok("");
            }
            // Needs the caller's terminal, or the caller's idea of "here".
            case GIT_CLONE, NEW_PROJECT, IDE_EDITOR -> Response.act(action.name());
            case BACK, EXIT -> Response.ok("");
        };
    }

    /**
     * The settings window, from a request thread.
     * <p>
     * The inner runLater is not redundant: {@code showOnFxThread} ends in {@code showAndWait}, which
     * blocks the FX thread in a nested event loop, and a DialogBuilder that blocks never lets
     * showDialog's own scheduling finish. Deferring by one pulse puts the nested loop outside the
     * builder, which is the contract that class documents.
     */
    static void showSettings(Settings settings) {
        JavaFXManager.getInstance().showDialog(
                (lock, holder) -> javafx.application.Platform.runLater(() -> {
                    var saved = SettingsDialog.showOnFxThread(null, settings);
                    if (saved) {
                        // The picker reads all three when it is CONSTRUCTED, and this process keeps
                        // that window alive for days. SettingsDialog marks the picker stale itself.
                        littlejlib.proj.ide.IdeDetector.forget();
                        littlejlib.proj.terminal.TerminalDetector.forget();
                    }
                    synchronized (lock) {
                        holder[0] = saved ? "SAVED" : "CANCELLED";
                        lock.notifyAll();
                    }
                }),
                3_600_000);
    }

    /** Where the CLIENT is. This process was started somewhere else, possibly days ago. */
    static Path cwdOf(Request request) {
        return Paths.get(request.cwd == null || request.cwd.isBlank()
                ? System.getProperty("user.dir") : request.cwd);
    }
}
