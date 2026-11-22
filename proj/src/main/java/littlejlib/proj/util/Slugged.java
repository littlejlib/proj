package littlejlib.proj.util;

/**
 * An enum that has a stable written spelling, and the two static helpers that let it be STORED as a
 * string while being USED as an enum.
 */
public interface Slugged {

    /** The spelling written to disk and accepted on a command line. */
    String slug();

    /**
     * The constant whose slug or name matches, case-insensitively; {@code fallback} when nothing
     * does, when the value is blank, or when it is null. Never throws - a settings file is hand
     * edited and a typo must degrade to the default rather than stop the program.
     */
    static <E extends Enum<E> & Slugged> E of(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        var v = value.trim();
        for (var e : type.getEnumConstants()) {
            if (e.slug().equalsIgnoreCase(v) || e.name().equalsIgnoreCase(v)) return e;
        }
        return fallback;
    }

    /** The form to store, with a null collapsing to the default rather than to a null column. */
    static String write(Slugged value, Slugged fallback) {
        var v = value == null ? fallback : value;
        return v == null ? "" : v.slug();
    }
}
