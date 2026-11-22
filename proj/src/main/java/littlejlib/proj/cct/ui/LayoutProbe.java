package littlejlib.proj.cct.ui;

import module java.base;

import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class LayoutProbe {

    private LayoutProbe() {}

    public static void dump(Stage stage, MainWindow window) {
        var wait = new PauseTransition(Duration.seconds(3));
        wait.setOnFinished(e -> report(stage, window));
        wait.play();
    }

    private static void report(Stage stage, MainWindow window) {
        var scene = stage.getScene();
        System.err.println("stage    " + stage.getWidth() + " x " + stage.getHeight());
        System.err.println("scene    " + scene.getWidth() + " x " + scene.getHeight());
        var root = (BorderPane) window.node();
        line("root", root);
        line("top", root.getTop());
        line("center", root.getCenter());
        line("bottom", root.getBottom());
        if (root.getCenter() instanceof javafx.scene.control.SplitPane sp) {
            for (var i = 0; i < sp.getItems().size(); i++) line("  item" + i, sp.getItems().get(i));
            System.err.println("  dividers " + Arrays.toString(sp.getDividerPositions()));
        }
    }

    private static void line(String name, Node n) {
        if (n == null) { System.err.println(name + "   (null)"); return; }
        var b = n.getBoundsInParent();
        var extra = "";
        if (n instanceof javafx.scene.layout.Region r)
            extra = "  min=" + r.getMinWidth() + " pref=" + r.getPrefWidth() + " max=" + r.getMaxWidth()
                + "  computedMin=" + r.minWidth(-1) + " computedPref=" + r.prefWidth(-1);
        System.err.println(name + "   " + round(b.getWidth()) + " x " + round(b.getHeight())
            + " at (" + round(b.getMinX()) + "," + round(b.getMinY()) + ")" + extra);
    }

    private static long round(double d) { return Math.round(d); }
}
