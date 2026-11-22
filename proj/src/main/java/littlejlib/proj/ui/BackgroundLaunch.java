package littlejlib.proj.ui;

import javafx.application.Platform;
import littlejlib.proj.terminal.RunOutcome;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Starting a terminal off the JavaFX thread.
 * <p>
 * Spawning a process used to return at once; opening Tabby does not. The tab has to appear and its
 * shell has to be ready before the command can be typed into it, which is a second and a half when
 * all is well and up to twenty when it is not. On the FX thread that is a frozen window.
 */
public final class BackgroundLaunch {

    private BackgroundLaunch() { }

    public static void run(Supplier<RunOutcome> launch, Consumer<RunOutcome> report) {
        var thread = new Thread(() -> {
            var outcome = RunOutcome.FAILED;
            try {
                outcome = launch.get();
            } catch (Exception e) {
                System.err.println("[BackgroundLaunch] " + e);
            }
            var result = outcome;
            Platform.runLater(() -> report.accept(result));
        }, "proj-launch");
        thread.setDaemon(true);
        thread.start();
    }
}
