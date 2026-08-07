package littlejlib.proj;

import littlejlib.proj.db.DB;
import littlejlib.proj.scanner.ProjectStore;
import littlejlib.proj.scanner.ProjectTreeScanner;

import java.nio.file.Path;

/**
 * Not a junit test - a runnable check that the ArcadeDB 26.7.3 + @ArcadeData roundtrip works
 * against the real on-disk database, including the pre-existing one written by the old schema.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SmokeScanStore" -Dexec.classpathScope=test -Dexec.args="C:/user/code/littlejlib"
 */
public class SmokeScanStore {

    public static void main(String[] args) {
        var codeDir = Path.of(args.length > 0 ? args[0] : "C:\\user\\code\\littlejlib");
        System.out.println("scanning " + codeDir);

        var scanner = new ProjectTreeScanner(4)
                .progress((folders, projects) -> System.out.print("\r  " + folders + " folders, " + projects + " projects"));
        var records = scanner.scan(codeDir);
        System.out.println("\nscanned " + records.size() + " projects in " + scanner.foldersScanned() + " folders");

        var store = new ProjectStore();
        var stamp = System.currentTimeMillis();
        store.replaceAll(codeDir, records, stamp);
        System.out.println("stored. count=" + store.count(codeDir));

        var loaded = store.load(codeDir);
        System.out.println("loaded " + loaded.size() + " rows");
        loaded.stream().limit(8).forEach(r -> System.out.println("  "
                + (r.maven() ? "M" : "-") + (r.gradle() ? "G" : "-") + (r.jbang() ? "J" : "-") + (r.git() ? "g" : "-")
                + "  " + pad(r.relativePath(), 34) + pad(r.coordinates(), 46) + r.gitSlug()));

        var mavenOnly = store.loadMavenOnly(codeDir);
        System.out.println("maven-only query returned " + mavenOnly.size());

        var one = loaded.isEmpty() ? null : store.byPath(loaded.get(0).path());
        System.out.println("byPath roundtrip: " + (one == null ? "NULL" : one.name() + " / parent=[" + one.parentPath() + "]"));

        System.out.println("re-running to confirm upsert does not duplicate...");
        store.replaceAll(codeDir, scanner.scan(codeDir), System.currentTimeMillis());
        System.out.println("count after second run=" + store.count(codeDir) + "  (must equal " + records.size() + ")");

        DB.get().shutdown();
        System.out.println("OK");
        System.exit(0);
    }

    static String pad(String s, int n) {
        var v = s == null ? "" : s;
        return v.length() >= n ? v.substring(0, n - 1) + " " : v + " ".repeat(n - v.length());
    }
}
