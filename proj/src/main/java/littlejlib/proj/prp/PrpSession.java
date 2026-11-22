package littlejlib.proj.prp;

import datapotter.arcadedbhelper.ArcadeData;
import datapotter.arcadedbhelper.TypeDef;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One claude-code conversation started on one PRP, recorded at the moment it was started rather than
 * found afterwards - proj mints the id and passes it as {@code --session-id}.
 * <p>
 * This is also where the tab name lives. There is no separate store of short names because there
 * does not need to be one: the name to offer for a PRP is the name its last session used, and the
 * project half of a name is the project half of the last name used anywhere in that project. Both
 * fall out of these rows, so "set it once" needs no second place to look.
 */
@ArcadeData
public final class PrpSession extends PrpSession_A {

    String sessionId;       // the uuid handed to claude-code, and the identity of the row
    String prpFile;         // absolute path of the PRP document
    String prpIndex;
    String projectPath;     // absolute project root, which is where the agent was started
    String tabName;
    Long startedAt;
    String exe;             // the explicit program it ran on (fclaude ...); null when it was the default

    public static final TypeDef<PrpSession> TYPEDEF =
            schemaBuilder()
                    .factory(PrpSession::new)
                    .unique($sessionId)
                    .lsmIndex($prpFile)
                    .lsmIndex($projectPath)
                    .__();

    public Path prp() {
        return prpFile == null || prpFile.isBlank() ? null : Paths.get(prpFile);
    }

    public Path project() {
        return projectPath == null || projectPath.isBlank() ? null : Paths.get(projectPath);
    }

    public long started() {
        return startedAt == null ? 0L : startedAt;
    }

    @Override
    public String toString() {
        return tabName + "  " + sessionId;
    }
}
