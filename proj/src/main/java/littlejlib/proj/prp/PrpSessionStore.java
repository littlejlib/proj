package littlejlib.proj.prp;

import com.arcadedb.database.Database;
import littlejlib.proj.db.DB;

import java.nio.file.Path;
import java.util.List;

import static littlejlib.proj.prp.PrpSession_A.*;
import static datapotter.arcadedbhelper.Query.query;
import static datapotter.arcadedbhelper.Upsert.upsert;

/**
 * Reads and writes {@link PrpSession} rows. Opens the database in THIS process, so it is only for a
 * process that may do that - see {@link PrpSessions}, which decides.
 */
public class PrpSessionStore {

    Database db() {
        return DB.get().getDatabase();
    }

    static String abs(Path p) {
        return p == null ? null : p.toAbsolutePath().normalize().toString();
    }

    public List<PrpSession> of(Path prpFile) {
        return query(db(), PrpSession.TYPEDEF).eq($prpFile, abs(prpFile))
                .orderByDesc($startedAt).list();
    }

    public PrpSession latestOf(Path prpFile) {
        var all = of(prpFile);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The newest session anywhere in this project, whichever PRP it was on. */
    public PrpSession latestInProject(Path projectDir) {
        var all = query(db(), PrpSession.TYPEDEF).eq($projectPath, abs(projectDir))
                .orderByDesc($startedAt).list();
        return all.isEmpty() ? null : all.get(0);
    }

    public PrpSession byId(String sessionId) {
        return query(db(), PrpSession.TYPEDEF).eq($sessionId, sessionId).firstOrNull();
    }

    public void record(String sessionId, Path prpFile, String prpIndex, Path projectDir,
                       String tabName, long startedAt) {
        record(sessionId, prpFile, prpIndex, projectDir, tabName, startedAt, null);
    }

    public void record(String sessionId, Path prpFile, String prpIndex, Path projectDir,
                       String tabName, long startedAt, String exe) {
        var db = db();
        db.transaction(() -> upsert(db, PrpSession.TYPEDEF)
                .key($sessionId, sessionId)
                .set($prpFile, abs(prpFile))
                .set($prpIndex, prpIndex)
                .set($projectPath, abs(projectDir))
                .set($tabName, tabName)
                .set($startedAt, startedAt)
                .set($exe, exe == null || exe.isBlank() ? null : exe.trim())
                .save());
    }

    /** A remembered tab name with today's project name in front, so renaming the project reaches old PRPs. */
    static String rekey(String tabName, String prpIndex, String key) {
        var old = TabName.projectPartOf(tabName, prpIndex);
        return old == null ? tabName : key + tabName.substring(old.length());
    }

    /**
     * What to call the tab: the name this PRP was last launched with, else a guess that inherits the
     * project's half from whatever was last used in the same project.
     */
    public String tabName(Path prpFile) {
        var mine = latestOf(prpFile);
        var project = AgentLauncher.projectRootOf(prpFile);
        var named = ProjFile.nameOf(project);
        if (mine != null && mine.tabName() != null && !mine.tabName().isBlank())
            return named == null ? mine.tabName() : rekey(mine.tabName(), mine.prpIndex(), named);

        var key = named != null ? named : new ProjectKeyStore().of(project);
        if (key == null) {
            // No key set, but a name used elsewhere in the project carries one in its first part -
            // which covers every row written before keys existed.
            var sibling = latestInProject(project);
            key = sibling == null ? null
                    : TabName.projectPartOf(sibling.tabName(), sibling.prpIndex());
        }
        return TabName.derive(prpFile, key);
    }
}
