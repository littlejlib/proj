package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.paint.Color;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.prp.GlobalPrpEntry;
import littlejlib.proj.terminal.TerminalRunner;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Photographs the agent launch window, and prints what each of its buttons would actually run.
 * <p>
 * The picture answers whether the choices read clearly. The printed argv answers the thing a picture
 * cannot: a terminal command that is subtly malformed looks perfectly fine as a button label, and
 * the only way to see it without starting a terminal is to read the resolved command line. That is
 * how PRP-26 found NetBeans being handed its own path as an argument.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.ui.SmokeAgentLaunch" -Dexec.classpathScope=test -Dexec.args="out.png [projectDir] [prpIndex]"
 */
public class SmokeAgentLaunch {

    public static void main(String[] args) throws Exception {
        var out = new File(args.length > 0 ? args[0] : "agent-launch.png");
        var project = Path.of(args.length > 1 ? args[1] : ".").toAbsolutePath().normalize();
        var wanted = args.length > 2 ? args[2] : null;

        var settings = new SettingsManager().loadSettings();
        var prps = GlobalPrpEntry.forProject(project);
        if (prps.isEmpty()) {
            System.out.println("no PRPs in " + project);
            return;
        }
        var prp = prps.stream().filter(p -> wanted == null || wanted.equals(p.getIndex()))
                .reduce((a, b) -> b).orElse(prps.get(prps.size() - 1));
        System.out.println("PRP " + prp.getIndex() + "  " + prp.getName());

        System.out.println("\nTerminals offered, in order:");
        for (var t : TerminalRunner.choices(settings)) {
            System.out.println("   " + (t.canRun() ? "RUN  " : "OPEN ") + t.getName());
            System.out.println("        open " + t.getOpenCommand());
            System.out.println("        run  " + (t.getRunCommand() == null ? "(none)" : t.getRunCommand()));
        }

        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();

        var done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                var stage = AgentLaunchDialog.build(prp.getMainFile(), settings);
                var root = stage.getScene().getRoot();
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
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();

        Platform.exit();
        System.out.println("\nwrote " + out.getAbsolutePath());
        System.exit(0);
    }
}
