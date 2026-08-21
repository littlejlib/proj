package littlejlib.proj.model;

import xyz.jphil.arcadedb.datahelper.ArcadeData;
import xyz.jphil.arcadedb.datahelper.TypeDef;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One project directory, one row.
 * <p>
 * The predecessor folded an entire code directory's tree into a single row as nested JSON, so the
 * cheapest possible question - "which of these are maven projects" - meant fetching and
 * deserializing everything. Here the fields are flat and indexed, so that question is an index hit,
 * and adding a field later is a new column rather than a new blob shape to version.
 * <p>
 * The tree is not stored as nesting either: {@code parentPath} carries the structure and the view
 * rebuilds it into {@code TreeItem}s, which is where a tree belongs at display time.
 */
@ArcadeData
public final class ProjectRecord extends ProjectRecord_A {

    String path;            // absolute, and the identity of the row
    String parentPath;      // absolute path of the enclosing node; "" at the top
    String codeDir;         // the configured code directory this was found under
    String name;
    String relativePath;    // relative to codeDir, which is what the picker shows
    int depth;

    boolean maven;
    boolean gradle;
    boolean jbang;
    boolean git;

    String groupId;
    String artifactId;
    String version;
    String packaging;
    String description;

    String gitUser;
    String gitOrg;
    String gitRepo;
    String gitRemote;       // the raw origin url, kept because org/repo alone cannot tell github from gitlab
    String gitHost;         // the host out of that url, lowercased; "" when there is no remote at all

    String baseType;        // CodeRepoBaseType of the coderepobase this came from, denormalised so it is queryable
    String org;             // first path segment under the code root; the grouping that matters here

    int openCount;          // how often this project has been picked - the only ranking signal
    Long lastOpened;        // that needs no configuration and is right about what you actually use

    Long lastScanned;
    Long lastModified;      // directory mtime, so "recently worked on" can be an ordering

    public static final TypeDef<ProjectRecord> TYPEDEF =
            schemaBuilder()
                    .factory(ProjectRecord::new)
                    .unique($path)
                    .lsmIndex($codeDir)
                    .lsmIndex($parentPath)
                    .lsmIndex($baseType)
                    .__();

    public boolean isSecondaryBase() {
        return !"PRIMARY".equalsIgnoreCase(baseType);
    }

    public Path dir() {
        return path == null || path.isBlank() ? null : Paths.get(path);
    }

    /**
     * A synthetic node standing for a whole code directory, used as a grouping row when more than
     * one is being browsed. Real rows are always found beneath their code directory, so only a
     * synthetic one can have an empty relativePath.
     */
    public boolean isCodeDirNode() {
        return relativePath == null || relativePath.isBlank();
    }

    /** Any build system at all, as opposed to a directory that merely contains projects. */
    public boolean isProject() {
        return maven || gradle || jbang || git;
    }

    /**
     * A plain directory: nothing that builds and nothing under version control. It is still a row -
     * an organisation directory, or a project early enough that it has neither yet - and everything
     * that does not need a pom to answer still works on it: cd, open, copy path, PRPs.
     */
    public boolean isFolder() {
        return !isCodeDirNode() && !isProject();
    }

    /**
     * Everything the view actually renders, and nothing that changes on its own. Timestamps are
     * excluded on purpose: a filesystem event that leaves this string identical is noise, and the
     * watcher uses that to avoid rewriting rows and repainting the table for nothing.
     */
    public String signature() {
        return String.join("|", nz(name), nz(relativePath), nz(parentPath),
                String.valueOf(maven), String.valueOf(gradle), String.valueOf(jbang), String.valueOf(git),
                nz(groupId), nz(artifactId), nz(version), nz(packaging), nz(description),
                nz(gitUser), nz(gitOrg), nz(gitRepo), nz(gitRemote), nz(gitHost));
    }

    /**
     * "Is it on GitHub" is three different questions, and they are three different concerns: never
     * put under version control at all, under git but never pushed anywhere, or pushed somewhere
     * that is not GitHub. Collapsing them into one boolean hides which of the three you are looking
     * at, which is the only thing that tells you what to do about it.
     */
    public GitState gitState() {
        if (!git) return GitState.NONE;
        if (isBlank(gitRemote)) return GitState.NO_REMOTE;
        return GitState.isGitHubHost(gitHost) ? GitState.GITHUB : GitState.OTHER_HOST;
    }

    /** The browsable url for the origin, when there is one that can be browsed. */
    public String gitWebUrl() {
        if (isBlank(gitHost) || isBlank(gitOrg) || isBlank(gitRepo)) return "";
        return "https://" + gitHost + "/" + gitOrg + "/" + gitRepo;
    }

    public String coordinates() {
        return isBlank(groupId) || isBlank(artifactId) ? "" : groupId + ":" + artifactId + ":" + nz(version);
    }

    public String gitSlug() {
        return isBlank(gitOrg) || isBlank(gitRepo) ? "" : gitOrg + "/" + gitRepo;
    }

    public String dependencyXml() {
        return "<dependency>\n"
                + "    <groupId>" + nz(groupId) + "</groupId>\n"
                + "    <artifactId>" + nz(artifactId) + "</artifactId>\n"
                + "    <version>" + nz(version) + "</version>\n"
                + "</dependency>\n";
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
