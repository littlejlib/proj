package littlejlib.proj.daemon;

import java.nio.file.Path;
import java.util.List;

/**
 * Bare "proj" - the main menu - through the daemon.
 * <p>
 * WHY THE MENU AND NOT JUST THE PICKER. The menu used to be shown by this process, and its first
 * option opened the project picker here too. That picker loads the index, the index is ArcadeDB,
 * and ArcadeDB in embedded mode takes an exclusive lock on its directory which the daemon is
 * already holding - so the option failed precisely when everything else was working. Moving the
 * menu itself across fixes that at the root rather than one option at a time, and costs nothing:
 * the daemon's JavaFX is already up, so the window appears instead of booting a toolkit first.
 * <p>
 * What comes back is one of three things. A path, when the user picked a project: the directory
 * change happens HERE, because PROJ_POSTRUN_BATCH belongs to the shell that launched this process
 * and a daemon cannot reach it. An action name, for the menu items that prompt on a terminal.
 * Or nothing, when the user just closed the menu.
 */
public final class MenuViaDaemon {

    private MenuViaDaemon() { }

    /**
     * @param exitCode what this process should exit with, when there is nothing further to do
     * @param action   a menu item the daemon could not carry out; null when there is none
     */
    public record Outcome(int exitCode, String action) { }

    /** Null when the daemon could not be reached, meaning the caller should run the menu itself. */
    public static Outcome run(boolean verbose) {
        try {
            var response = Client.send(List.of("menu"), true, m -> {
                if (verbose) System.out.println("[proj] " + m);
            });

            if (!response.stderr.isBlank()) System.err.println(response.stderr);
            if (!response.stdout.isBlank()) System.out.println(response.stdout);

            if (response.chosenPath != null && !response.chosenPath.isBlank()) {
                OpenViaDaemon.changeDirectory(Path.of(response.chosenPath));
            }
            return new Outcome(response.exitCode, response.action);

        } catch (Exception e) {
            if (verbose) System.err.println("[proj] daemon unavailable (" + e.getMessage() + "), running in-process");
            return null;
        }
    }
}
