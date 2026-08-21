package littlejlib.proj;

import littlejlib.proj.db.DB;
import littlejlib.proj.scanner.ProjectStore;
import littlejlib.proj.scanner.ProjectTreeScanner;
import littlejlib.proj.scanner.ProjectWatcher;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates and deletes a project under a scratch root while the watcher is running, and checks the
 * stored rows follow. A watcher that compiles proves nothing about whether it fires.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SmokeWatcher" -Dexec.classpathScope=test -Dexec.args="<scratchdir>"
 */
public class SmokeWatcher {

    static final String POM = "<project><modelVersion>4.0.0</modelVersion>"
            + "<groupId>io.github.test</groupId><artifactId>watched-demo</artifactId>"
            + "<version>${rev}</version><properties><rev>3.2.1</rev></properties>"
            + "<description>created while the watcher was running</description></project>";

    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]).resolve("watchroot");
        deleteTree(root);
        Files.createDirectories(root);

        var store = new ProjectStore();
        store.replaceAll(root, new ProjectTreeScanner(4).scan(root), System.currentTimeMillis());
        System.out.println("baseline rows under scratch root: " + store.count(root));

        var watcher = new ProjectWatcher(4)
                .onChange(recs -> recs.forEach(r -> System.out.println("  EVENT -> " + r.name()
                        + "  maven=" + r.maven() + "  " + r.coordinates() + "  " + r.description())));
        watcher.start(java.util.List.of(root));
        Thread.sleep(1200);

        System.out.println("\ncreating a maven project while watching...");
        var proj = root.resolve("demo-project");
        Files.createDirectories(proj);
        Files.writeString(proj.resolve("pom.xml"), POM);
        Thread.sleep(4000);
        System.out.println("rows now: " + store.count(root)
                + "   demo-project stored? " + (store.byPath(proj.toAbsolutePath().toString()) != null));

        System.out.println("\ndeleting it while watching...");
        deleteTree(proj);
        Thread.sleep(4000);
        System.out.println("rows now: " + store.count(root)
                + "   demo-project stored? " + (store.byPath(proj.toAbsolutePath().toString()) != null));

        watcher.stop();
        deleteTree(root);
        DB.get().shutdown();
        System.exit(0);
    }

    static void deleteTree(Path p) throws Exception {
        if (!Files.exists(p)) return;
        try (var walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                try { Files.deleteIfExists(f); } catch (Exception ignored) { }
            });
        }
    }
}
