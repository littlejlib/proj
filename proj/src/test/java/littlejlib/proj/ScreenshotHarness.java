package littlejlib.proj;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import littlejlib.proj.ui.ProjectPickerDialog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/// Takes the pictures in docs/screenshots: the real picker window over INVENTED data. It runs with a
/// scratch user.home (a fresh settings file, a fresh index) over a made-up code folder, touches no real
/// project, login or network, and saves the pictures itself, so a changed look is one command:
///
///     mvn -q test-compile package -DskipTests
///     java -cp "target/littlejlib-proj-<version>.jar;target/test-classes" littlejlib.proj.ScreenshotHarness ../docs/screenshots
public class ScreenshotHarness {

    public static void main(String[] args) throws Exception {
        var out = Path.of(args.length > 0 ? args[0] : "docs/screenshots").toAbsolutePath();
        Files.createDirectories(out);
        var home = Files.createTempDirectory("proj-demo-home");
        System.setProperty("user.home", home.toString());
        var code = home.resolve("code");
        DemoTree.build(code);

        var settingsFile = littlejlib.proj.util.AppPaths.settings();
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, String.join("\n",
                "scanDepth = 3",
                "singleFileEditor = ''",
                "defaultTerminal = ''",
                "globalHotkey = 'off'",
                "codebase.primary = ['" + code + "']",
                "codebase.mirror = []",
                "codebase.auxiliary = []",
                ""));
        var settings = new SettingsManager().loadSettings();
        var paths = List.of(code);

        var shots = new String[]{"picker", "picker-prps"};
        var done = new CountDownLatch(1);
        var t = new Thread(() -> {
            try {
                Thread.sleep(16000);   // the first open builds the toolkit and both indexes
                for (int i = 0; i < shots.length; i++) {
                    if (i == 1) key(KeyCode.DIGIT2, true);
                    if (i == 1) Thread.sleep(6000);
                    Thread.sleep(2500);
                    snapshot(out.resolve(shots[i] + ".png"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
                System.exit(0);
            }
        }, "screenshots");
        t.setDaemon(true);
        t.start();
        ProjectPickerDialog.show(paths, settings);
        done.await();
    }

    private static void key(KeyCode code, boolean alt) {
        Platform.runLater(() -> {
            for (var w : Window.getWindows()) {
                if (!w.isShowing() || w.getScene() == null) continue;
                var target = w.getScene().getFocusOwner() != null ? w.getScene().getFocusOwner() : w.getScene();
                Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, alt, false));
            }
        });
    }

    private static void snapshot(Path file) throws Exception {
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                for (var w : Window.getWindows()) {
                    if (!w.isShowing() || w.getScene() == null || w.getWidth() < 400) continue;
                    var img = w.getScene().getRoot().snapshot(null, null);
                    var bi = new BufferedImage((int) img.getWidth(), (int) img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    var pr = img.getPixelReader();
                    for (int y = 0; y < bi.getHeight(); y++)
                        for (int x = 0; x < bi.getWidth(); x++) bi.setRGB(x, y, pr.getArgb(x, y));
                    ImageIO.write(bi, "png", file.toFile());
                    System.out.println("wrote " + file);
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
    }
}
