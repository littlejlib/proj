package littlejlib.proj.daemon;

import littlejlib.proj.TerminalHelper;

import java.nio.file.Path;
import java.util.List;

/**
 * "proj o" through the daemon.
 * <p>
 * THE POINT OF THIS CLASS: the daemon picks, this process acts. PROJ_POSTRUN_BATCH is an
 * environment variable of the shell that launched this client, and proj.bat runs that script after
 * this process exits. A daemon started from another shell hours ago cannot write it, cannot read
 * it, and cannot change your terminal's directory. So the selection travels back as a path and the
 * directory change happens here.
 */
public final class OpenViaDaemon {

    private OpenViaDaemon() { }

    /** Returns an exit code, or null when the daemon could not be reached and a fallback should run. */
    public static Integer open(boolean verbose) {
        return open(verbose, null);
    }

    /** {@code tab} names the tab to land on, which is how {@code proj cct} arrives; null is the default. */
    public static Integer open(boolean verbose, String tab) {
        try {
            var request = tab == null ? List.of("open") : List.of("open", tab);
            var response = Client.send(request, true, m -> {
                if (verbose) System.out.println("[proj] " + m);
            });

            if (!response.stderr.isBlank()) System.err.println(response.stderr);
            if (!response.stdout.isBlank()) System.out.println(response.stdout);

            if (response.chosenPath != null && !response.chosenPath.isBlank()) {
                changeDirectory(Path.of(response.chosenPath));
            }
            return response.exitCode;

        } catch (Exception e) {
            if (verbose) System.err.println("[proj] daemon unavailable (" + e.getMessage() + "), running in-process");
            return null;
        }
    }

    static void changeDirectory(Path target) {
        System.err.println("Selected: " + target);
        littlejlib.proj.Navigate.to(target);
    }
}
