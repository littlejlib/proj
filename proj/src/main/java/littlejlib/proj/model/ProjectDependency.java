package littlejlib.proj.model;

import datapotter.arcadedbhelper.ArcadeData;
import datapotter.arcadedbhelper.TypeDef;

/**
 * One {@code <dependency>} of one project, flat, one row.
 * <p>
 * Stored rather than read from the pom on demand because the interesting questions are asked across
 * the whole set at once - which projects depend on something that is not published, which projects
 * depend on each other - and answering those by opening seven hundred poms is not a query, it is a
 * scan. Indexed on {@code ga} so the reverse question ("who depends on this artifact") costs the
 * same as the forward one.
 * <p>
 * {@code version} may legitimately be absent: a dependency whose version comes from a
 * dependencyManagement section we could not reach has no version here, and that is recorded as
 * unresolved rather than filled in with a guess. A guessed version would produce a confident wrong
 * answer from the Maven Central lookup, which is worse than no answer.
 */
@ArcadeData
public final class ProjectDependency extends ProjectDependency_A {

    String path;            // the project directory that declares this dependency
    String ga;              // groupId:artifactId - the identity that survives a version change
    String groupId;
    String artifactId;
    String version;         // null when it could not be resolved
    String scope;           // compile / provided / runtime / test / system / import
    boolean optional;
    boolean managed;        // the version came from a dependencyManagement section, not the tag itself

    public static final TypeDef<ProjectDependency> TYPEDEF =
            schemaBuilder()
                    .factory(ProjectDependency::new)
                    .lsmIndex($path)
                    .lsmIndex($ga)
                    .__();

    public static String ga(String groupId, String artifactId) {
        return nz(groupId) + ":" + nz(artifactId);
    }

    public boolean versionResolved() {
        return version != null && !version.isBlank() && !version.contains("${");
    }

    /** The coordinates a Central lookup needs, or "" when the version is unknown. */
    public String coordinates() {
        return versionResolved() ? groupId + ":" + artifactId + ":" + version : "";
    }

    /**
     * A test dependency that is missing from Central does not stop you publishing, so the
     * publishability concern deliberately ignores these.
     */
    public boolean isRuntimeFacing() {
        return !"test".equalsIgnoreCase(nz(scope)) && !"provided".equalsIgnoreCase(nz(scope)) && !optional;
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
