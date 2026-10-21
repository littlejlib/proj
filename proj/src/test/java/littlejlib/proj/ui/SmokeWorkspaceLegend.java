package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.paint.Color;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.group.Groups;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Photographs a PLAIN group's pane, for the legend along its foot. The rows are left empty on
 * purpose - what is being checked is that the keys it offers are all listed, on the tab where a
 * non-code project actually lives.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.ui.SmokeWorkspaceLegend" -Dexec.classpathScope=test -Dexec.args="out.png"
 */
public class SmokeWorkspaceLegend {

    public static void main(String[] args) throws Exception {
        var out = new File(args.length > 0 ? args[0] : "workspace-legend.png");
        var settings = new SettingsManager().loadSettings();
        var plain = Groups.of(settings).stream().filter(g -> !g.isCode()).findFirst().orElse(null);
        if (plain == null) {
            System.out.println("no plain group configured - nothing to photograph");
            return;
        }
        System.out.println("group " + plain.displayLabel());
        // Opened here rather than left to the pane: the scan bar reads its last-scan stamp inline
        // when the store is already open in this process and off-thread when it is not, and only
        // the first of those finishes before a photograph is taken.
        littlejlib.proj.db.DB.get();

        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();

        var done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                var pane = new WorkspacePane(plain, settings);
                pane.setRows(List.of());
                var root = pane.root;
                root.setPrefSize(900, 300);
                new Scene(root);
                root.applyCss();
                root.layout();

                var params = new SnapshotParameters();
                params.setFill(Color.WHITE);
                var image = root.snapshot(params, null);
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
