package littlejlib.proj.ui;

import javafx.scene.control.TextInputDialog;
import littlejlib.proj.model.ProjectRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * mm's "replicate project structure": copy a project's pom under a new artifactId, with an empty
 * source tree, as a sibling.
 */
public final class ProjectReplicator {

    private ProjectReplicator() { }

    public static void promptAndReplicate(ProjectRecord rec) {
        var dialog = new TextInputDialog();
        dialog.setTitle("Replicate project structure");
        dialog.setHeaderText("Source: " + rec.name());
        dialog.setContentText("New project name:");

        dialog.showAndWait()
                .map(String::trim)
                .filter(n -> !n.isEmpty())
                .ifPresent(name -> new Thread(() -> replicate(rec, name), "Replicate project").start());
    }

    static void replicate(ProjectRecord rec, String newName) {
        var src = rec.dir();
        var dst = src.resolveSibling(newName);

        if (Files.exists(dst)) {
            Toast.show("Cannot replicate: " + newName + " already exists");
            return;
        }

        try {
            Files.createDirectories(dst.resolve("src/main/java"));
            Files.writeString(dst.resolve("pom.xml"), rewritePom(src, rec, newName), UTF_8, StandardOpenOption.CREATE);
            Toast.show("Replicated " + rec.name() + " as " + newName);
        } catch (Exception e) {
            Toast.show("Failed to replicate " + rec.name() + ": " + e.getMessage());
        }
    }

    static String rewritePom(Path src, ProjectRecord rec, String newName) throws Exception {
        var pom = Files.readString(src.resolve("pom.xml"), UTF_8);
        var tagged = "<artifactId>" + rec.artifactId() + "</artifactId>";

        if (pom.contains(tagged)) return pom.replace(tagged, "<artifactId>" + newName + "</artifactId>");
        if (rec.artifactId() != null && pom.contains(rec.artifactId())) return pom.replace(rec.artifactId(), newName);
        throw new IllegalStateException("could not find the artifactId in the source pom");
    }
}
