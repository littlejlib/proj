package littlejlib.proj.prp;

import com.arcadedb.database.Database;
import littlejlib.proj.db.DB;

import java.nio.file.Path;

import static littlejlib.proj.prp.ProjectKey_A.*;
import static datapotter.arcadedbhelper.Query.query;
import static datapotter.arcadedbhelper.Upsert.upsert;

/** Reads and writes {@link ProjectKey} rows. Opens the database here - see {@link PrpSessions}. */
public class ProjectKeyStore {

    Database db() {
        return DB.get().getDatabase();
    }

    static String abs(Path p) {
        return p == null ? null : p.toAbsolutePath().normalize().toString();
    }

    /** The key set for this project, or null. */
    public String of(Path project) {
        var row = query(db(), ProjectKey.TYPEDEF).eq($path, abs(project)).firstOrNull();
        return row == null || row.key() == null || row.key().isBlank() ? null : row.key();
    }

    /** Another existing folder that holds this key, or null. Rows of folders that moved are skipped. */
    public String ownerOf(String key, Path project) {
        var me = abs(project);
        return query(db(), ProjectKey.TYPEDEF).eq($key, key).list().stream()
                .map(ProjectKey::path)
                .filter(p -> p != null && !p.equals(me) && java.nio.file.Files.isDirectory(Path.of(p)))
                .findFirst().orElse(null);
    }

    public void set(Path project, String key) {
        var db = db();
        db.transaction(() -> upsert(db, ProjectKey.TYPEDEF)
                .key($path, abs(project))
                .set($key, key)
                .set($updatedAt, System.currentTimeMillis())
                .save());
    }
}
