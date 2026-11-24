package littlejlib.proj.prp;

import littlejlib.proj.store.Tables;

import java.nio.file.Path;
import java.util.Objects;

/** Reads and writes {@link ProjectKey} rows: a file in proj's data folder. */
public class ProjectKeyStore {

    static String abs(Path p) {
        return p == null ? null : p.toAbsolutePath().normalize().toString();
    }

    /** The key set for this project, or null. */
    public String of(Path project) {
        var me = abs(project);
        var row = Tables.projectKeys().first(k -> Objects.equals(me, k.path()));
        return row == null || row.key() == null || row.key().isBlank() ? null : row.key();
    }

    /** Another existing folder that holds this key, or null. Rows of folders that moved are skipped. */
    public String ownerOf(String key, Path project) {
        var me = abs(project);
        return Tables.projectKeys().where(k -> Objects.equals(key, k.key())).stream()
                .map(ProjectKey::path)
                .filter(p -> p != null && !p.equals(me) && java.nio.file.Files.isDirectory(Path.of(p)))
                .findFirst().orElse(null);
    }

    public void set(Path project, String key) {
        var me = abs(project);
        Tables.projectKeys().update(rows -> {
            var row = rows.stream().filter(k -> Objects.equals(me, k.path())).findFirst().orElse(null);
            if (row == null) rows.add(row = new ProjectKey().path(me));
            row.key(key).updatedAt(System.currentTimeMillis());
        });
    }
}
