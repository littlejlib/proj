package littlejlib.proj;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import littlejlib.proj.prp.GlobalPrpEntry;
import littlejlib.proj.ui.PrpAction;
import littlejlib.proj.ui.PrpPanel;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Renders the real {@link PrpPanel} offscreen against a real prp folder, and writes a PNG.
 * <p>
 * Two things can only be answered by looking. Whether the action bar under a row appears for the
 * SELECTED row and stays collapsed on the others - the whole point of the change, and invisible to
 * any assertion that does not measure pixels. And whether the four new {@link PrpAction} glyphs
 * exist in this JavaFX on this machine: a missing one is drawn as a silent empty box, which is
 * indistinguishable from a present one in the source. Run before shipping an icon, not after.
 * <p>
 * No Stage is shown, so this steals no focus from whatever the user is doing.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.SmokePrpPanel" -Dexec.classpathScope=test -Dexec.args="out.png [projectDir]"
 */
public class SmokePrpPanel {

    public static void main(String[] args) throws Exception {
        var out = new File(args.length > 0 ? args[0] : "prp-panel.png");
        var project = Path.of(args.length > 1 ? args[1] : ".").toAbsolutePath().normalize();

        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();

        var done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                write(out, project);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();

        Platform.exit();
        System.out.println("wrote " + out.getAbsolutePath());
        System.exit(0);
    }

    static void write(File out, Path project) throws Exception {
        var settings = new SettingsManager().loadSettings();
        // GLOBAL mode when the file name says so, because that is the list where a row has to
        // identify its own project and where the ticket therefore has to be readable.
        var global = out.getName().contains("global");
        var panel = new PrpPanel(settings, global, global ? null : () -> { });

        var entries = GlobalPrpEntry.forProject(project);
        System.out.println(entries.size() + " PRPs read from " + project.resolve("prp"));
        // setEntries rather than showProject: the latter reads the folder on a background thread,
        // and there is nothing here to wait on it.
        panel.setEntries(entries);
        panel.showFocus(true);

        var icons = new VBox(6);
        icons.setPadding(new Insets(12));
        icons.setStyle("-fx-background-color: white;");
        icons.getChildren().add(new Label("PRP ICONS AT 28px - a box here means the glyph is missing"));
        var big = new FlowPane(12, 4);
        for (var a : PrpAction.values()) {
            big.getChildren().add(littlejlib.proj.ui.Icons.of(a.icon, 28, a.iconColor));
        }
        icons.getChildren().add(big);

        var page = new VBox(0, panel.root, icons);
        new Scene(page, 640, 780);
        page.applyCss();
        page.layout();

        var params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        var image = page.snapshot(params, null);

        var w = (int) image.getWidth();
        var h = (int) image.getHeight();
        var buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var reader = image.getPixelReader();
        for (var y = 0; y < h; y++) {
            for (var x = 0; x < w; x++) buffered.setRGB(x, y, reader.getArgb(x, y));
        }
        javax.imageio.ImageIO.write(buffered, "png", out);
    }
}
