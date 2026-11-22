package littlejlib.proj.cct.ui;

import module java.base;

import javafx.application.*;
import javafx.scene.Scene;
import javafx.stage.*;

/**
 * The transcripts pane in a window of its own. It is NOT how this ships - the tab inside proj is -
 * but a standalone window can be started, driven and killed without a daemon or a project index in
 * the way, which is what makes it worth keeping for testing.
 * <p>
 * Reached with {@code java -cp <proj jar> littlejlib.proj.cct.ui.CctApp}.
 */
public final class CctApp extends Application {

    private MainWindow window;
    private final Tray tray = new Tray();
    private Stage main;
    private boolean trayed;

    @Override
    public void start(Stage stage) {
        main = stage;
        window = new MainWindow();
        var visible = Screen.getPrimary().getVisualBounds();
        var w = Math.min(1300, visible.getWidth() - 40);
        var h = Math.min(820, visible.getHeight() - 40);
        var scene = new Scene(window.node(), w, h);
        Theme.applyLight(scene);
        Keys.disableMnemonics(window.node());
        window.actions().install(scene);
        stage.setTitle("transcripts - find the claude-code conversation you half-remember");
        stage.getIcons().addAll(AppIcon.sizes());
        stage.setScene(scene);
        stage.setMinWidth(820);
        stage.setMinHeight(520);
        stage.setOnCloseRequest(e -> {
            if (!trayed) return;
            e.consume();
            hide();
        });
        // show() FIRST, then the tray. Installing the AWT SystemTray on the JavaFX thread before
        // show() intermittently meant the stage was never created at all: process alive, FX thread
        // idle in its run loop, no exception, AWT tray windows present and no stage window.
        stage.show();
        stage.centerOnScreen();
        window.queryField().requestFocus();
        window.start();
        installTrayLater();
        if (System.getenv("CCT_LAYOUT_DEBUG") != null) LayoutProbe.dump(stage, window);
    }

    private void installTrayLater() {
        if (System.getenv("CCT_NO_TRAY") != null) return;
        var t = new Thread(() -> {
            var ok = tray.install(() -> Platform.runLater(this::reveal),
                () -> Platform.runLater(this::quit));
            if (ok) Platform.runLater(() -> { trayed = true; Platform.setImplicitExit(false); });
        }, "cct-tray");
        t.setDaemon(true);
        t.start();
    }

    private void hide() {
        main.hide();
        window.release();
        tray.note("transcripts is still here", "Click this icon to reopen.");
    }

    private void reveal() {
        if (main.isShowing()) { main.requestFocus(); return; }
        main.show();
        main.centerOnScreen();
        window.queryField().requestFocus();
        window.start();
    }

    private void quit() {
        window.close();
        tray.remove();
        Platform.exit();
    }

    @Override
    public void stop() {
        if (window != null) window.close();
        tray.remove();
    }

    public static void main(String[] args) { launchGui(args); }

    public static void launchGui(String[] args) {
        Application.launch(CctApp.class, args);
    }
}
