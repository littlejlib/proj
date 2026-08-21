package littlejlib.proj.cct.export;

import module java.base;

/**
 * A time with its ZONE, because a log without one is a log that cannot be lined up against anything.
 * <p>
 * The house rule for threaded discussions says date AND time AND timezone, for the stated reason
 * that the user, the servers and the people being reported to are not in one place. It applies here
 * for a second reason: these logs are read next to each other across cycles, and two of them stamped
 * only "16:55" cannot be ordered if one was written in Nairobi and one somewhere else.
 * <p>
 * The short zone name is preferred - EAT, IST, CET - because that is what the user writes by hand.
 * When the JDK has no abbreviation for the zone it falls back to the offset, which is unambiguous.
 */
public final class Stamp {

    private Stamp() {}

    static final ZoneId ZONE = ZoneId.systemDefault();

    static final DateTimeFormatter FULL =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE);

    /** e.g. {@code 2026-08-21 16:55 EAT}. Empty for no time at all, never a fake one. */
    public static String of(long millis) {
        return millis <= 0 ? "" : FULL.format(Instant.ofEpochMilli(millis)) + " " + zone(millis);
    }

    /** e.g. {@code 16:55 EAT} - for a line that already carries the date. */
    public static String clock(long millis) {
        if (millis <= 0) return "";
        var full = of(millis);
        return full.length() > 11 ? full.substring(11) : full;
    }

    /**
     * {@code EAT}, not {@code GMT+03:00}.
     * <p>
     * Locale.ENGLISH, and that is the whole trick: with {@code Locale.ROOT} the JDK has no
     * abbreviation table and returns {@code GMT+03:00} for Africa/Nairobi. English is the right
     * locale to ask anyway, since these abbreviations are what the user writes by hand. When even
     * English has no name for the zone the offset is used, which is never wrong, only longer.
     */
    public static String zone(long millis) {
        var at = Instant.ofEpochMilli(millis <= 0 ? System.currentTimeMillis() : millis);
        var name = TimeZone.getTimeZone(ZONE).getDisplayName(
            ZONE.getRules().isDaylightSavings(at), TimeZone.SHORT, Locale.ENGLISH);
        return name == null || name.isBlank() || name.startsWith("GMT") ? offset(at) : name;
    }

    static String offset(Instant at) {
        var offset = ZONE.getRules().getOffset(at).getId();
        return "UTC" + ("Z".equals(offset) ? "+00:00" : offset);
    }

    public static String now() { return of(System.currentTimeMillis()); }
}
