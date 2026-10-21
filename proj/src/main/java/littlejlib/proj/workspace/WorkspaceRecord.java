package littlejlib.proj.workspace;

import datapotter.arcadedbhelper.ArcadeData;
import datapotter.arcadedbhelper.TypeDef;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One folder of a PLAIN group, one row.
 */
@ArcadeData
public final class WorkspaceRecord extends WorkspaceRecord_A {

    String path;            // absolute, and the identity of the row
    String parentPath;      // nearest kept ancestor; "" directly under the base
    String base;            // the configured base directory this was found under
    String groupSlug;       // which PLAIN group it belongs to, denormalised so it is queryable
    String name;
    String relativePath;    // relative to the base, which is what the tab shows
    int depth;

    boolean hasPrp;         // has a prp directory with at least one .md in it
    boolean dated;          // name carries a date - the other way a folder says it is a piece of work
    boolean container;      // not a project itself, but projects live under it - a repo of projects
    int prpCount;

    int openCount;
    Long lastOpened;

    Long lastScanned;
    Long lastActivity;      // newest mtime in the prp folder - "when did I last work on this"

    public static final TypeDef<WorkspaceRecord> TYPEDEF =
            schemaBuilder()
                    .factory(WorkspaceRecord::new)
                    .unique($path)
                    .lsmIndex($base)
                    .lsmIndex($groupSlug)
                    .lsmIndex($parentPath)
                    .__();

    public Path dir() {
        return path == null || path.isBlank() ? null : Paths.get(path);
    }

    /** A piece of work rather than a container: it has PRPs, or its name is dated, or both. */
    public boolean isProject() {
        return hasPrp || dated;
    }

    /**
     * Neither a project nor noise: a directory that HOLDS projects, the way an organisation
     * directory does on the code side. Folders such as "archive" or "clients" are these - they were being greyed as
     * uninteresting when they are the containers the interesting things live in.
     */
    public boolean isContainer() {
        return container && !isProject();
    }

    public Path prpDir() {
        var d = dir();
        return d == null ? null : d.resolve("prp");
    }

    /** A synthetic node standing for a whole base, used as the grouping row above its folders. */
    public boolean isBaseNode() {
        return relativePath == null || relativePath.isBlank();
    }

    public long activity() {
        return lastActivity == null ? 0L : lastActivity;
    }

    @Override
    public String toString() {
        return name + (hasPrp ? " (" + prpCount + " prp)" : "");
    }
}
