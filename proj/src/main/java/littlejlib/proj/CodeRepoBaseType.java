package littlejlib.proj;

/**
 * What kind of coderepobase a directory is - a base of code repositories, the way a database is a
 * base of data.
 * <p>
 * All three are indexed and searchable. The type decides only what you see WITHOUT asking, because
 * the cost of a big mirror is noise in the results rather than time on the clock.
 */
public enum CodeRepoBaseType {

    PRIMARY("Primary", "your own code; always shown"),
    GITHUB_MIRROR("GitHub mirror", "the clone cache behind proj g; hidden unless included"),
    AUXILIARY("Auxiliary", "archives and other code you keep but rarely open; hidden unless included");

    public final String label, description;

    CodeRepoBaseType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public boolean shownByDefault() {
        return this == PRIMARY;
    }

    /** Only the primary base is worth a live filesystem watch. */
    public boolean watched() {
        return this == PRIMARY;
    }

    public static CodeRepoBaseType parse(String value, CodeRepoBaseType fallback) {
        if (value == null || value.isBlank()) return fallback;
        var v = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "PRIMARY", "MAIN" -> PRIMARY;
            case "MIRROR", "GITHUB_MIRROR", "GITHUBMIRROR", "GITHUB", "REFERENCE" -> GITHUB_MIRROR;
            case "AUXILIARY", "AUX", "ARCHIVE", "SECONDARY" -> AUXILIARY;
            default -> fallback;
        };
    }

    /** The spelling accepted on the command line and written to Settings.toml. */
    public String slug() {
        return switch (this) {
            case PRIMARY -> "primary";
            case GITHUB_MIRROR -> "mirror";
            case AUXILIARY -> "auxiliary";
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
