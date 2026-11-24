package littlejlib.proj.store;

import littlejlib.proj.model.CentralArtifact;
import littlejlib.proj.model.FileRecord;
import littlejlib.proj.model.ProjectDependency;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.prp.ProjectKey;
import littlejlib.proj.prp.PrpSession;
import littlejlib.proj.util.AppPaths;
import littlejlib.proj.workspace.WorkspaceRecord;

import java.nio.file.Path;

/**
 * Where each table lives. {@code cache/} is rebuilt by a scan and may be deleted at any time;
 * {@code data/} is written by proj and nothing else has it, so it is never deleted by a rebuild.
 */
public final class Tables {

    private Tables() { }

    public static Path cache() {
        return AppPaths.home().resolve("cache");
    }

    public static Path data() {
        return AppPaths.home().resolve("data");
    }

    public static Table<ProjectRecord> projects() {
        return Table.of(cache().resolve("projects.bin"), ProjectRecord.class);
    }

    public static Table<ProjectDependency> dependencies() {
        return Table.of(cache().resolve("dependencies.bin"), ProjectDependency.class);
    }

    public static Table<FileRecord> files() {
        return Table.of(cache().resolve("files.bin"), FileRecord.class).unheld();
    }

    public static Table<CentralArtifact> central() {
        return Table.of(cache().resolve("central.bin"), CentralArtifact.class);
    }

    public static Table<WorkspaceRecord> workspaces() {
        return Table.of(cache().resolve("workspaces.bin"), WorkspaceRecord.class);
    }

    public static Table<PrpSession> prpSessions() {
        return Table.of(data().resolve("prp-sessions.json"), PrpSession.class);
    }

    public static Table<ProjectKey> projectKeys() {
        return Table.of(data().resolve("project-keys.json"), ProjectKey.class);
    }
}
