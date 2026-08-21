package littlejlib.proj.model;

import xyz.jphil.arcadedb.datahelper.ArcadeData;
import xyz.jphil.arcadedb.datahelper.TypeDef;

/**
 * Whether one set of maven coordinates exists on Maven Central.
 * <p>
 * Keyed by coordinates rather than by directory, because that is what the answer is actually about:
 * it stays valid when a project is moved or renamed, and two checkouts of the same artifact share
 * one lookup. mm declared this column and never wired it up, so it has always rendered empty.
 */
@ArcadeData
public final class CentralArtifact extends CentralArtifact_A {

    String coords;          // groupId:artifactId:version
    boolean published;
    Long checkedAt;
    Integer httpStatus;

    public static final TypeDef<CentralArtifact> TYPEDEF =
            schemaBuilder()
                    .factory(CentralArtifact::new)
                    .unique($coords)
                    .__();

    /**
     * A published artifact is immutable on Central, so a positive answer never expires. A negative
     * one does - the project may simply not have been released yet.
     */
    public boolean isFresh(long negativeTtlMillis) {
        if (checkedAt == null) return false;
        return published || System.currentTimeMillis() - checkedAt < negativeTtlMillis;
    }
}
