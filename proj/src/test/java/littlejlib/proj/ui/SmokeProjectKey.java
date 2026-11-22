package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Photographs the project-key dialog. It explains a convention rather than asking a question, so
 * whether it reads clearly is the whole of what it has to get right.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.ui.SmokeProjectKey" -Dexec.classpathScope=test -Dexec.args="out.png [projectDir]"
 */
public class SmokeProjectKey {

    public static void main(String[] args) throws Exception {
        var out = new File(args.length > 0 ? args[0] : "project-key.png");
        var project = Path.of(args.length > 1 ? args[1] : ".").toAbsolutePath().normalize();

        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();

        var done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                var pane = ProjectDialog.build(project).getDialogPane();
                // A DialogPane needs a Scene before it will lay out, and it has none until shown.
                var holder = new VBox(pane);
                new Scene(holder);
                holder.applyCss();
                holder.layout();

                var params = new SnapshotParameters();
                params.setFill(Color.WHITE);
                var image = holder.snapshot(params, null);
                var w = (int) image.getWidth();
                var h = (int) image.getHeight();
                var buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                var reader = image.getPixelReader();
                for (var y = 0; y < h; y++) {
                    for (var x = 0; x < w; x++) buffered.setRGB(x, y, reader.getArgb(x, y));
                }
                javax.imageio.ImageIO.write(buffered, "png", out);
                System.out.println("wrote " + out.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        System.exit(0);
    }
}
