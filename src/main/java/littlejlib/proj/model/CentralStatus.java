package littlejlib.proj.model;

/**
 * Display state of the Maven Central column. UNKNOWN and CHECKING are deliberately distinct from
 * ABSENT: a blank cell because nobody has looked yet must not read as "not published".
 */
public enum CentralStatus {

    NOT_MAVEN(""),
    UNKNOWN("?"),
    CHECKING("..."),
    PUBLISHED("✓"),
    ABSENT("-");

    public final String glyph;

    CentralStatus(String glyph) {
        this.glyph = glyph;
    }
}
