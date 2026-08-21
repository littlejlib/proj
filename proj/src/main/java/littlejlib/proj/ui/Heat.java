package littlejlib.proj.ui;

import javafx.scene.control.Cell;

/**
 * How recently something was worked on, as a row colour: warm for today, cooling to pale blue over
 * about a month.
 * <p>
 * WHY RECENCY AND NOT FREQUENCY. The question the colour answers is "what am I in the middle of",
 * and the projects you open most often are not that - they are the ones you have always opened, and
 * ranking by count paints them permanently red until the colour stops distinguishing anything. Open
 * count still ranks the list under Relevance; it does not tint it.
 * <p>
 * THE CURVE IS LOGARITHMIC, which matters more than the palette. On a linear scale over thirty days
 * everything touched this week lands in the top fifth of the range and is one indistinguishable
 * shade - and this week is the only part anybody is asking about. Log spreads it: today 1.0,
 * yesterday 0.80, three days 0.60, a week 0.39, a fortnight 0.21, a month 0.
 * <p>
 * THE SELECTED ROW KEEPS ITS TEMPERATURE. JavaFX's default selection is a dark blue, and blue is
 * this scale's coldest colour - so selecting the hottest row in the table turned it the colour of
 * the coldest, which is a small thing that has to be reasoned past every single time. The selected
 * row is painted a DARK version of its own heat instead: deep red at the top of the scale, deep blue
 * at the bottom.
 * <p>
 * Done through {@code -fx-background} and {@code -fx-selection-bar} rather than
 * {@code -fx-background-color}, which is the detail that makes it work. Modena derives its text
 * colour with {@code ladder(-fx-background, ...)}, so writing the background through that variable
 * gets black text on the pale shades and white text on the dark ones for free. Setting
 * {@code -fx-background-color} directly would paint the same pixels and leave black text on a deep
 * red selection.
 */
public final class Heat {

    private Heat() { }

    static final double WINDOW_DAYS = 30.0;
    static final double SCALE = Math.log(1 + WINDOW_DAYS);
    static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** 1.0 for something touched now, 0.0 for a month ago or older. -1 when nothing is known. */
    public static double of(Long lastActivityMillis) {
        return of(lastActivityMillis, System.currentTimeMillis());
    }

    public static double of(Long lastActivityMillis, long now) {
        if (lastActivityMillis == null || lastActivityMillis <= 0) return -1;
        var ageDays = Math.max(0.0, (now - lastActivityMillis) / (double) DAY_MS);
        return clamp(1 - Math.log(1 + ageDays) / SCALE);
    }

    /** The newest of several timestamps, any of which may be absent. Null when all of them are. */
    public static Long newest(Long... times) {
        Long best = null;
        for (var t : times) {
            if (t == null || t <= 0) continue;
            if (best == null || t > best) best = t;
        }
        return best;
    }

    // Red through amber to a blue so pale it reads as unpainted, which is the point: a month-old
    // project should look like an ordinary row, not like a row someone has coloured blue. The whole
    // range is light because this sits UNDER black text all day.
    static final double[] STOPS = {1.00, 0.80, 0.60, 0.40, 0.20, 0.00};
    static final int[][] COLOURS = {
            {0xFF, 0xCD, 0xD2},   // red 100
            {0xFF, 0xE0, 0xB2},   // orange 100
            {0xFF, 0xF3, 0xC4},   // amber
            {0xFF, 0xFD, 0xE7},   // pale yellow
            {0xF1, 0xF7, 0xFC},   // barely blue
            {0xE8, 0xF1, 0xFA},   // blue 50-ish
    };

    // The same walk down the same scale, dark enough for white text throughout. Not the light
    // palette multiplied down: those shades are so pale that scaling them produces six greys, and
    // the whole point of the selected colour is that it still says which end of the scale it is.
    static final int[][] SELECTED = {
            {0xB7, 0x1C, 0x1C},   // deep red
            {0xC6, 0x48, 0x0F},   // deep orange
            {0x96, 0x69, 0x0A},   // deep amber
            {0x5A, 0x64, 0x6E},   // slate
            {0x20, 0x50, 0x8C},   // deep blue
            {0x0D, 0x47, 0xA1},   // blue 900
    };

    /** The CSS colour for a heat, or null when there is nothing to say. */
    public static String colour(double heat) {
        return colour(heat, COLOURS);
    }

    /** The dark counterpart, worn by the row while it is selected. */
    public static String selectedColour(double heat) {
        return colour(heat, SELECTED);
    }

    static String colour(double heat, int[][] table) {
        if (heat < 0) return null;
        for (var i = 0; i < STOPS.length - 1; i++) {
            var hi = STOPS[i];
            var lo = STOPS[i + 1];
            if (heat > lo || i == STOPS.length - 2) {
                return mix(table[i + 1], table[i], clamp((heat - lo) / (hi - lo)));
            }
        }
        return hex(table[table.length - 1]);
    }

    /**
     * Paints a row or cell, and un-paints it - the same call has to do both, because these are
     * recycled: a cell that scrolls from a hot row onto a cold one keeps the old style otherwise.
     * <p>
     * Both colours are set at once and the selector decides which is used, so nothing here has to
     * be re-run when the selection moves.
     */
    public static void paint(Cell<?> cell, double heat) {
        // Chosen here rather than left to the :selected rule, because an INLINE style outranks every
        // stylesheet rule in JavaFX - modena's `.tree-cell:filled:selected { -fx-background:
        // -fx-selection-bar-non-focused }` never gets to fire against one. Measured: setting the
        // light colour inline left the selected row pale, indistinguishable from its neighbours.
        // Which is why callers must repaint on a selection change as well as on a new item.
        var colour = cell.isSelected() ? selectedColour(heat) : colour(heat);
        cell.setStyle(colour == null ? "" : "-fx-background: " + colour + ";");
    }

    static String mix(int[] a, int[] b, double t) {
        return hex(new int[]{
                (int) Math.round(a[0] + (b[0] - a[0]) * t),
                (int) Math.round(a[1] + (b[1] - a[1]) * t),
                (int) Math.round(a[2] + (b[2] - a[2]) * t)});
    }

    static String hex(int[] rgb) {
        return String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }

    static double clamp(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
