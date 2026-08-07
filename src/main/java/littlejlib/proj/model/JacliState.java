package littlejlib.proj.model;

/**
 * How jacli currently regards a project. PINNED is a stated preference (jacli index --watch);
 * HOT is observed membership of the daemon's warm set and evaporates on its own.
 */
public enum JacliState {

    UNINDEXED("", "not indexed by jacli"),
    INDEXED("○", "indexed, cold"),
    HOT("●", "hot - warm in the jacli daemon"),
    PINNED("★", "pinned - jacli watches this project and keeps it fresh");

    public final String glyph, description;

    JacliState(String glyph, String description) {
        this.glyph = glyph;
        this.description = description;
    }

    public boolean isPinned() {
        return this == PINNED;
    }

    public boolean isKnown() {
        return this != UNINDEXED;
    }
}
