package littlejlib.proj;

import littlejlib.proj.db.DB;

import java.util.List;

/**
 * Drops document types no longer declared in code. Currently just ProjectTreeCacheEntry, the
 * single-row whole-tree blob that ProjectRecord replaced.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.DropOrphanTypes" -Dexec.classpathScope=test
 */
public class DropOrphanTypes {

    static final List<String> ORPHANS = List.of("ProjectTreeCacheEntry");

    public static void main(String[] args) {
        var db = DB.get().getDatabase();
        for (var name : ORPHANS) {
            if (!db.getSchema().existsType(name)) {
                System.out.println(name + ": not present, nothing to do");
                continue;
            }
            var rows = db.countType(name, true);
            try {
                db.command("sql", "DROP TYPE " + name + " UNSAFE");
                System.out.println(name + ": dropped (" + rows + " rows discarded)");
            } catch (Exception e) {
                System.out.println(name + ": could not drop - " + e.getMessage());
            }
        }
        System.out.println("remaining types: " + db.getSchema().getTypes().stream().map(t -> t.getName()).toList());
        DB.get().shutdown();
        System.exit(0);
    }
}
