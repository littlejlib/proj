package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.paint.Color;
import littlejlib.proj.workspace.WorkspaceRecord;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Photographs the PLAIN group's tree table with real nesting - two and three levels deep - which
 * {@link SmokeWorkspaceLegend} deliberately leaves empty. What this checks is the thing a compiler
 * cannot: that the disclosure arrow and the indentation land on the Folder column and not on the
 * row-number column (PRP 34 - the tree column was never retargeted here the way the projects table
 * retargets its own).
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.ui.SmokeWorkspaceTree" -Dexec.classpathScope=test -Dexec.args="out.png"
 */
public class SmokeWorkspaceTree {

    public static void main(String[] args) throws Exception {
        var out = new File(args.length > 0 ? args[0] : "workspace-tree.png");
        var base = "D:\\shared\\docs";

        var archive = row(base, base + "\\Archive", "", "Archive", true, false, 0, 0);
        var case1 = row(base, base + "\\Archive\\case-2026-01-05", archive.path(), "case-2026-01-05",
                false, true, 3, System.currentTimeMillis());
        var sub = row(base, base + "\\Archive\\case-2026-01-05\\filings", case1.path(), "filings",
                false, false, 0, 0);
        var social = row(base, base + "\\2026-06-social-media", "", "2026-06-social-media",
                false, true, 1, System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000);
        var rows = List.of(archive, case1, sub, social);

        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();

        var done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                var table = new TreeTableView<WorkspaceRecord>();
                WorkspaceColumns.install(table);
                var root = new TreeItem<WorkspaceRecord>(null);
                WorkspaceTree.build(root, rows, true);
                root.setExpanded(true);
                table.setRoot(root);
                table.setPrefSize(760, 220);
                new Scene(table);
                table.applyCss();
                table.layout();

                var params = new SnapshotParameters();
                params.setFill(Color.WHITE);
                var image = table.snapshot(params, null);
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

    static WorkspaceRecord row(String base, String path, String parentPath, String name,
                               boolean container, boolean hasPrp, int prpCount, long activity) {
        var rec = new WorkspaceRecord();
        rec.path(path).base(base).groupSlug("docs").name(name)
                .relativePath(path.substring(base.length() + 1).replace('\\', '/'))
                .parentPath(parentPath).container(container).hasPrp(hasPrp).prpCount(prpCount)
                .lastActivity(activity);
        return rec;
    }
}
