package littlejlib.proj;

import littlejlib.proj.db.DB;

import java.util.List;

/**
 * Drops document types no longer declared in code. With no arguments that is just
 * ProjectTreeCacheEntry, the single-row whole-tree blob that ProjectRecord replaced.
 * <p>
 * Named types can be given instead, which is the repair for a type whose INDEX has gone bad rather
 * than one that is obsolete - a transaction that dies mid-commit can leave index entries pointing at
 * records that were never written, and every later read of that index throws RecordNotFound. Any
 * type still declared in DB.initSchema is recreated empty on the next start, so dropping a derived
 * one costs a rescan and nothing else. Never do this to ProjectRecord: openCount and lastOpened are
 * the only things here that no scan can rebuild.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.DropOrphanTypes" -Dexec.classpathScope=test
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.DropOrphanTypes" -Dexec.classpathScope=test -Dexec.args="FileRecord"
 */
public class DropOrphanTypes {

    static final List<String> ORPHANS = List.of("ProjectTreeCacheEntry");

    public static void main(String[] args) {
        var db = DB.get().getDatabase();
        for (var name : args.length == 0 ? ORPHANS : List.of(args)) {
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
