package littlejlib.proj;

import littlejlib.proj.db.DB;

/**
 * Prints every document type actually present in the on-disk database, with its row count and
 * indexes. Diagnostic for "is there one data structure here or several".
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SchemaDump" -Dexec.classpathScope=test
 */
public class SchemaDump {

    public static void main(String[] args) {
        var db = DB.get().getDatabase();
        System.out.println("types on disk:");
        for (var t : db.getSchema().getTypes()) {
            System.out.println("  " + t.getName() + "   rows=" + db.countType(t.getName(), true));
            t.getAllIndexes(true).forEach(i -> System.out.println("      index " + i.getName()));
            System.out.println("      props: " + String.join(", ", t.getPropertyNames()));
        }
        DB.get().shutdown();
        System.exit(0);
    }
}
