package littlejlib.proj.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.util.Duration;
import littlejlib.proj.prp.AgentExe;
import littlejlib.proj.prp.PrpResume;
import littlejlib.proj.prp.PrpSession;

/**
 * A session that ran on fclaude and is now resumed without saying which claude: ask, and let silence mean
 * plain claude. Resuming it on the wrong account by default is the quiet mistake this guards against, and
 * waiting for an answer forever would block a launch nobody is looking at.
 */
final class ExeConfirm {

    private ExeConfirm() { }

    static final int SECONDS = 10;

    /**
     * The explicit program to resume {@code session} on, or null for the default. Must run on the FX thread.
     *
     * @throws IllegalArgumentException when the chosen program is no longer on PATH
     */
    static String programFor(PrpSession session) {
        var chosen = PrpResume.exeFor(session, null, ExeConfirm::ask);
        return chosen == null ? null : AgentExe.require(chosen);
    }

    /** True only for an explicit yes. */
    static boolean ask(String exe) {
        if (!Platform.isFxApplicationThread()) return false;
        var yes = new ButtonType("Resume on " + exe, ButtonBar.ButtonData.YES);
        var no = new ButtonType("Plain claude", ButtonBar.ButtonData.NO);
        var alert = new Alert(Alert.AlertType.CONFIRMATION, "", yes, no);
        alert.setTitle("Which claude?");
        alert.setHeaderText("This conversation ran on " + exe);
        var left = new int[] {SECONDS};
        Runnable text = () -> alert.setContentText("Resume it on " + exe + "? With no answer it resumes on plain claude in "
                + left[0] + " s.");
        text.run();
        var clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (--left[0] <= 0) alert.close();
            else text.run();
        }));
        clock.setCycleCount(SECONDS);
        clock.play();
        try {
            return alert.showAndWait().filter(b -> b == yes).isPresent();
        } finally {
            clock.stop();
        }
    }
}
