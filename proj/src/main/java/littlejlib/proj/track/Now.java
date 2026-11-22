package littlejlib.proj.track;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Times as stored (ISO with the offset, so the zone is never lost) and as shown. */
public final class Now {

    private Now() { }

    static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static String iso() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static String shown(String iso) {
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(SHOWN);
        } catch (RuntimeException e) {
            return iso == null ? "" : iso;
        }
    }

    public static String shown(long millis) {
        return millis <= 0 ? "" : Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(SHOWN);
    }

    public static Instant instant(String iso) {
        try {
            return iso == null ? null : OffsetDateTime.parse(iso).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "40m", "3h", "2d": how long ago, for a reader. */
    public static String age(Instant then, Instant now) {
        var m = Math.max(0, Duration.between(then, now).toMinutes());
        return m < 60 ? m + "m" : m < 48 * 60 ? m / 60 + "h" : m / 1440 + "d";
    }

    /** Whole days since then; -1 when it cannot be read. */
    public static long ageDays(String iso) {
        try {
            return Duration.between(OffsetDateTime.parse(iso), OffsetDateTime.now()).toDays();
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
