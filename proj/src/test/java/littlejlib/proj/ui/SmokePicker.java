package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.paint.Color;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.model.ProjectRecord;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;

/**
 * Builds the real picker window offscreen and photographs all three tabs.
 * <p>
 * The window is constructed but never shown, which is the only way to look at it from here without
 * taking the screen away from whatever the user is doing. It is also the only check that reaches
 * what a compiler cannot see: that the tab shell holds, that the split divides where it was told to,
 * that the heat colours are legible under black text, and that the two lazily-built tabs do not
 * throw the first time they are selected.
 * <p>
 * The PROJECTS table is empty here - loading is driven by {@code reopen()}, which needs a shown
 * window and a live database. What this proves about that tab is the frame, not the contents. The
 * other two load their own data and are fully real.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.ui.SmokePicker" -Dexec.classpathScope=test -Dexec.args="out.png [projectDir]"
 */
public class SmokePicker {

    public static void main(String[] args) throws Exception {
        var out = new File(args.length > 0 ? args[0] : "picker.png");
        var project = Path.of(args.length > 1 ? args[1] : ".").toAbsolutePath().normalize();

        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();

        var settings = new SettingsManager().loadSettings();
        var picker = onFx(() -> new ProjectPickerDialog(
                settings.getBrowsePaths().stream().map(Path::of).toList(), settings));
        var root = onFx(() -> picker.stage.getScene().getRoot());

        // The picker normally fills `records` from the database, which the running daemon has locked
        // exclusively. Two levels of directory listing stands in for it here - enough for the global
        // tab to take its fast path (stat each known project) rather than the tree-walk fallback,
        // which is the path the real window takes and therefore the one worth photographing.
        onFx(() -> {
            picker.records = twoLevelsUnder(settings.getBrowsePaths());
            picker.sortBox.setValue(ProjectSort.HOTTEST);
            picker.repopulate();
            System.out.println("stand-in index: " + picker.records.size() + " directories");
            return null;
        });

        onFx(() -> {
            var rec = new ProjectRecord()
                    .path(project.toString()).relativePath(project.getFileName().toString())
                    .name(project.getFileName().toString()).maven(true).git(true)
                    .groupId("io.github.littlejlib").artifactId("littlejlib-proj").version("1.0");
            picker.openPrpPanel(rec, false);
            try {
                picker.prpPanel.setEntries(littlejlib.proj.prp.GlobalPrpEntry.forProject(project));
            } catch (Exception e) {
                e.printStackTrace();
            }
            picker.prpPanel.showFocus(false);
            return null;
        });
        shootTab(picker, root, picker.tabs.projects, out, null);
        System.out.println("tabs: " + onFx(() -> picker.tabs.pane.getTabs().stream()
                .map(t -> t.getText()).toList())
                + "   split items: " + onFx(() -> picker.split.getItems().size())
                + "   panel open: " + onFx(picker::prpPanelOpen));

        // Straight to "PRP and agent", the one settings tab this PRP added controls to.
        onFx(() -> {
            picker.tabs.select(picker.tabs.settings);
            var inner = (javafx.scene.control.TabPane) ((javafx.scene.layout.VBox)
                    picker.tabs.settings.getContent()).getChildren().get(0);
            inner.getSelectionModel().select(3);
            return null;
        });
        shootTab(picker, root, picker.tabs.settings, out, "settings");

        // Each group contributes two tabs; groups.get(1) is the code group.s PRPs. Selecting it starts
        // a scan of every code base on a background thread - waited for HERE and not on the FX
        // thread, which is the thread that has to deliver the result.

        var codePrps = picker.tabs.groups.get(1);
        onFx(() -> {
            picker.tabs.select(codePrps.tab);
            return null;
        });
        for (var waited = 0; waited < 240 && onFx(() -> codePrps.prpPanel.entries.isEmpty()); waited++) {
            Thread.sleep(250);
        }
        System.out.println("code group PRP entries: " + onFx(() -> codePrps.prpPanel.entries.size())
                + " of " + onFx(() -> codePrps.prpPanel.globalTotal) + " found");
        shootTab(picker, root, codePrps.tab, out, "group-prps");

        Platform.exit();
        System.exit(0);
    }

    static java.util.List<ProjectRecord> twoLevelsUnder(java.util.List<String> bases) {
        var out = new java.util.ArrayList<ProjectRecord>();
        for (var base : bases) {
            var root = Path.of(base);
            for (var org : children(root)) {
                out.add(record(root, org));
                for (var child : children(org)) out.add(record(root, child));
            }
        }
        return out;
    }

    static java.util.List<Path> children(Path dir) {
        try (var s = java.nio.file.Files.list(dir)) {
            return s.filter(java.nio.file.Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith(".")).toList();
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    static ProjectRecord record(Path root, Path dir) {
        // The real mtime, so the heat colours in the picture are the ones this machine would
        // actually show rather than a gradient invented for the photograph.
        long modified;
        try {
            modified = java.nio.file.Files.getLastModifiedTime(dir).toMillis();
        } catch (Exception e) {
            modified = 0;
        }
        return new ProjectRecord().path(dir.toString()).name(dir.getFileName().toString())
                .relativePath(root.relativize(dir).toString()).codeDir(root.toString())
                .parentPath(dir.getParent() == null ? "" : dir.getParent().toString())
                .baseType("PRIMARY").lastModified(modified);
    }

    static <T> T onFx(java.util.function.Supplier<T> work) throws Exception {
        var task = new FutureTask<>(work::get);
        Platform.runLater(task);
        return task.get();
    }

    static void shootTab(ProjectPickerDialog picker, javafx.scene.Node root,
                         javafx.scene.control.Tab tab, File out, String suffix) throws Exception {
        var file = suffix == null ? out : new File(out.getParentFile(), stem(out) + "-" + suffix + ".png");
        onFx(() -> {
            picker.tabs.select(tab);
            // The stage is never shown, so nothing has ever laid this out.
            ((javafx.scene.Parent) root).applyCss();
            ((javafx.scene.Parent) root).layout();
            try {
                shoot(root, file);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
        System.out.println("wrote " + file.getAbsolutePath());
    }

    static String stem(File f) {
        var n = f.getName();
        var dot = n.lastIndexOf('.');
        return dot < 0 ? n : n.substring(0, dot);
    }

    static void shoot(javafx.scene.Node root, File out) throws Exception {
        var params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        params.setViewport(new javafx.geometry.Rectangle2D(0, 0, 1500, 820));
        var image = root.snapshot(params, null);

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
