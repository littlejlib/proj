package littlejlib.proj.prp;

import littlejlib.proj.store.Tables;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reads and writes {@link PrpSession} rows: a file in proj's data folder, which any process may use.
 */
public class PrpSessionStore {

    static String abs(Path p) {
        return p == null ? null : p.toAbsolutePath().normalize().toString();
    }

    static List<PrpSession> newestFirst(List<PrpSession> rows) {
        return rows.stream().sorted(Comparator.comparingLong(
                (PrpSession s) -> s.startedAt() == null ? 0L : s.startedAt()).reversed()).toList();
    }

    public List<PrpSession> of(Path prpFile) {
        var file = abs(prpFile);
        return newestFirst(Tables.prpSessions().where(s -> Objects.equals(file, s.prpFile())));
    }

    public PrpSession latestOf(Path prpFile) {
        var all = of(prpFile);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The newest session anywhere in this project, whichever PRP it was on. */
    public PrpSession latestInProject(Path projectDir) {
        var dir = abs(projectDir);
        var all = newestFirst(Tables.prpSessions().where(s -> Objects.equals(dir, s.projectPath())));
        return all.isEmpty() ? null : all.get(0);
    }

    public PrpSession byId(String sessionId) {
        return Tables.prpSessions().first(s -> Objects.equals(sessionId, s.sessionId()));
    }

    /** Every row whose PRP file sits directly in this folder. */
    public List<PrpSession> inFolder(Path prpDir) {
        var dir = prpDir.toAbsolutePath().normalize();
        return Tables.prpSessions().all().stream()
                .filter(s -> s.prp() != null && dir.equals(s.prp().toAbsolutePath().normalize().getParent())).toList();
    }

    /** Points an existing row at a renumbered PRP; false when there is no such row. */
    public boolean repoint(String sessionId, Path prpFile, String prpIndex, String tabName) {
        if (byId(sessionId) == null) return false;
        upsert(sessionId, s -> s.prpFile(abs(prpFile)).prpIndex(prpIndex).tabName(tabName));
        return true;
    }

    public void record(String sessionId, Path prpFile, String prpIndex, Path projectDir,
                       String tabName, long startedAt) {
        record(sessionId, prpFile, prpIndex, projectDir, tabName, startedAt, null);
    }

    public void record(String sessionId, Path prpFile, String prpIndex, Path projectDir,
                       String tabName, long startedAt, String exe) {
        upsert(sessionId, s -> s.prpFile(abs(prpFile)).prpIndex(prpIndex).projectPath(abs(projectDir))
                .tabName(tabName).startedAt(startedAt).exe(exe == null || exe.isBlank() ? null : exe.trim()));
    }

    static void upsert(String sessionId, Consumer<PrpSession> change) {
        Tables.prpSessions().update(rows -> {
            var row = rows.stream().filter(s -> Objects.equals(sessionId, s.sessionId())).findFirst().orElse(null);
            if (row == null) rows.add(row = new PrpSession().sessionId(sessionId));
            change.accept(row);
        });
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
