package littlejlib.proj;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.SnapshotParameters;
import littlejlib.proj.ui.ProjectAction;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
 * Renders every {@link ProjectAction} icon offscreen and writes a PNG, so "does this glyph exist in
 * this JavaFX on this machine" is answered by looking rather than by hoping.
 * <p>
 * It matters because JavaFX has no colour-font support and falls back per glyph: an emoji either
 * arrives as a monochrome outline, which is fine, or as a tofu box, which is not - and the two are
 * indistinguishable from the source. Run this before adding an icon, not after shipping one.
 * <p>
 * No Stage is shown. A Node only has to be in a Scene to be snapshotted, so this steals no focus
 * from whatever the user is doing.
 *
 * {@link #CANDIDATES} is how an icon gets chosen: put a dozen in, run, keep the ones that are not
 * boxes. Unicode 6.0 emoji mostly survive here and the 2018-and-later additions mostly do not - 🧩
 * and 🧬 were both boxes, which is what this found before they reached the picker.
 * <p>
 * The candidates are a constant rather than a command-line argument on purpose: emoji do not
 * survive the Windows command line, and arrive as "??". This file is UTF-8 and they survive it.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.SmokeIcons" -Dexec.classpathScope=test -Dexec.args="out.png"
 */
public class SmokeIcons {

    static final String CANDIDATES = "";

    public static void main(String[] args) throws Exception {
        var out = new File(args.length > 0 ? args[0] : "icons.png");
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();

        var done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                write(out);
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

    /** One row's action bar, built exactly as ActionBarCell builds it, visibility rule included. */
    static FlowPane bar(littlejlib.proj.model.ProjectRecord rec) {
        var bar = new FlowPane(2, 2);
        for (var a : ProjectAction.values()) {
            var b = new Label(a.rowText());
            b.setGraphic(littlejlib.proj.ui.Icons.of(a.icon, 12, a.iconColor));
            b.setGraphicTextGap(3);
            b.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-background-radius: 3;" + a.style);
            b.setVisible(a.supportedBy(rec));
            bar.getChildren().add(b);
        }
        return bar;
    }

    static void write(File out) throws Exception {
        var rows = new VBox(10);
        rows.setPadding(new Insets(12));
        rows.setStyle("-fx-background-color: white;");

        rows.getChildren().add(new Label("ROW BUTTONS - what one table row shows"));
        rows.getChildren().add(bar(new littlejlib.proj.model.ProjectRecord()
                .path("C:\\code\\a-project").relativePath("a-project").name("a-project")
                .maven(true).groupId("io.github.x").artifactId("x").version("1.0")));

        // The other half of what this proves: an unsupported action is invisible but still holds its
        // slot, so F4 sits at the same x on every row whether or not that row has a pom.
        rows.getChildren().add(new Label("A PLAIN FOLDER ROW - unsupported actions hidden, slots kept"));
        rows.getChildren().add(bar(new littlejlib.proj.model.ProjectRecord()
                .path("C:\\code\\some-folder").relativePath("some-folder").name("some-folder")));

        rows.getChildren().add(new Label("LEGEND - key, icon, full name"));
        var legend = new FlowPane(4, 3);
        for (var a : ProjectAction.values()) {
            var l = new Label(a.legendText());
            l.setGraphic(littlejlib.proj.ui.Icons.of(a.icon, 13, a.iconColor));
            l.setGraphicTextGap(4);
            l.setStyle("-fx-font-size: 9px; -fx-padding: 1 5 1 5; -fx-background-radius: 3;" + a.style);
            legend.getChildren().add(l);
        }
        rows.getChildren().add(legend);

        // The real menu chips, not a copy of them - same factory the ContextMenu uses.
        rows.getChildren().add(new Label("CONTEXT MENU - same tints as the legend"));
        var menu = new javafx.scene.layout.VBox(2);
        for (var a : ProjectAction.values()) menu.getChildren().add(littlejlib.proj.ui.ActionContextMenu.chipFor(a));
        // Printed as well as drawn: a wrong command line looks perfectly fine as a menu entry, and
        // the only way to see it without actually launching an IDE is to read the argv.
        System.out.println("\nOpen in - resolved launch commands:");
        for (var ide : littlejlib.proj.ui.ProjectPickerColumns.detectIdes(
                new littlejlib.proj.SettingsManager().loadSettings())) {
            menu.getChildren().add(littlejlib.proj.ui.ActionContextMenu.chipFor(ide));
            System.out.println("   " + ide.name + "  ->  " + ide.command());
            System.out.println("      argv " + java.util.List.of(
                    littlejlib.proj.util.Cmd.parse(ide.command().replace("%PATH%", "C:\\code\\demo"))));
        }
        menu.setMaxWidth(260);
        rows.getChildren().add(menu);

        // Big, so a tofu box cannot hide behind nine pixels.
        rows.getChildren().add(new Label("ICONS AT 28px - a box here means the glyph is missing"));
        var big = new FlowPane(10, 4);
        for (var a : ProjectAction.values()) big.getChildren().add(littlejlib.proj.ui.Icons.of(a.icon, 28, a.iconColor));
        rows.getChildren().add(big);

        if (!CANDIDATES.isEmpty()) {
            rows.getChildren().add(new Label("CANDIDATES - keep the ones that are not boxes"));
            var tried = new FlowPane(10, 4);
            // By code point, not by char: every one of these is a surrogate pair.
            CANDIDATES.codePoints().forEach(cp -> {
                var l = new Label(new String(Character.toChars(cp)));
                l.setStyle("-fx-font-size: 28px;");
                tried.getChildren().add(l);
            });
            rows.getChildren().add(tried);
        }

        new Scene(rows, 900, 620);
        var params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        var image = rows.snapshot(params, null);

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
