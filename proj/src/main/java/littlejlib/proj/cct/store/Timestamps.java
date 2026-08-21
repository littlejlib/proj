package littlejlib.proj.cct.store;

import module java.base;

public final class Timestamps {

    private Timestamps() {}

    private static final DateTimeFormatter LOCAL =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter LOCAL_DAY =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    public static long millis(String iso) {
        if (iso == null || iso.isBlank()) return 0L;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            } catch (DateTimeParseException e2) {
                return 0L;
            }
        }
    }

    public static String local(long millis) {
        return millis <= 0 ? "" : LOCAL.format(Instant.ofEpochMilli(millis));
    }

    public static String localDay(long millis) {
        return millis <= 0 ? "" : LOCAL_DAY.format(Instant.ofEpochMilli(millis));
    }

    public static long startOfDay(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long endOfDay(LocalDate d) {
        return d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
    }

    public static String ago(long millis) {
        if (millis <= 0) return "";
        var mins = Duration.between(Instant.ofEpochMilli(millis), Instant.now()).toMinutes();
        if (mins < 60) return mins + "m ago";
        if (mins < 60 * 24) return (mins / 60) + "h ago";
        var days = mins / (60 * 24);
        if (days < 30) return days + "d ago";
        return (days / 30) + "mo ago";
    }
}
