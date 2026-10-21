package littlejlib.proj.util;

import java.lang.management.ManagementFactory;

/**
 * Milliseconds since this JVM started - one shared clock for startup-timing logs, so a line in
 * {@code Server} and a line in {@code PickerHost} can be read on the same timeline without either
 * having to be handed the other's start time.
 */
public final class Boot {

    private Boot() { }

    static final long START = ManagementFactory.getRuntimeMXBean().getStartTime();

    public static long ms() {
        return System.currentTimeMillis() - START;
    }
}
