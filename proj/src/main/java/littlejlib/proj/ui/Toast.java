package littlejlib.proj.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Transient confirmation in the window's status line rather than a modal alert.
 * <p>
 * mm popped an Alert with an OK button for things like "copied to clipboard", which means a mouse
 * trip or a keypress to dismiss a message you did not need to acknowledge. Given the hand
 * condition, a message that clears itself is the right shape.
 */
public final class Toast {

    private Toast() { }

    static Label target;
    static PauseTransition pending;

    public static void bind(Label statusLabel) {
        target = statusLabel;
    }

    public static void show(String message) {
        if (target == null) {
            System.out.println(message);
            return;
        }
        Platform.runLater(() -> {
            target.setText(message);
            if (pending != null) pending.stop();
            pending = new PauseTransition(Duration.seconds(3));
            pending.setOnFinished(e -> target.setText(""));
            pending.play();
        });
    }
}
