package littlejlib.proj.prp;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Where a PRP stands, as written in its head (PRP 001R): {@code prp_status: in_progress}. A closed set, in the
 * style of Jira and ClickUp; anything else is refused when set and ignored when read.
 * <p>
 * {@link PrpStatus} (active or closed) is what the rest of proj asks; {@link #status()} maps this onto it.
 */
public enum PrpState {
    /** Created, nobody worked on it yet. */
    NEW("new"),
    IN_PROGRESS("in_progress"),
    /** Built, waiting for the user to confirm it works. */
    IN_REVIEW("in_review"),
    /** Paused by choice. */
    ON_HOLD("on_hold"),
    /** Waiting on something outside the PRP. */
    BLOCKED("blocked"),
    CLOSED("closed"),
    /** Dropped without doing it. */
    CANCELLED("cancelled");

    private final String key;

    PrpState(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** True for the two states a PRP does not come back from by itself. */
    public boolean finished() {
        return this == CLOSED || this == CANCELLED;
    }

    public PrpStatus status() {
        return finished() ? PrpStatus.CLOSED : PrpStatus.ACTIVE;
    }

    /** The state named by a head value (any case, {@code in-progress} accepted), or null for blank or unknown. */
    public static PrpState parse(String value) {
        if (value == null) return null;
        var v = value.strip().toLowerCase().replace('-', '_').replace(' ', '_');
        for (var s : values()) if (s.key.equals(v)) return s;
        return null;
    }

    /** {@link #parse} that refuses what it does not know, naming the allowed values. */
    public static PrpState require(String value) {
        var s = parse(value);
        if (s == null) throw new IllegalArgumentException("unknown PRP status '" + value + "'; one of " + keys());
        return s;
    }

    public static String keys() {
        return Arrays.stream(values()).map(PrpState::key).collect(Collectors.joining(", "));
    }
}
