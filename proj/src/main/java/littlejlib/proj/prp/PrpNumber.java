package littlejlib.proj.prp;

import java.util.regex.Pattern;

/**
 * A PRP number: four symbols of pure base34 (0-9, then A-Z without I and O, upper case), counted
 * {@code 0001}..{@code 0009}, {@code 000A}..{@code 000Z}, {@code 0010}.. up to {@code ZZZZ}.
 * Typed input is any width (padded on the left), any case, {@code o} read as 0 and {@code i} as 1.
 * <p>
 * LEGACY: the two-character scheme of folders not yet renumbered ({@code 00}-{@code 99}, then
 * {@code A0}..{@code ZZ}). A code's length tells the two apart: 4 is new, 2 is legacy.
 */
public final class PrpNumber {

    private PrpNumber() { }

    public static final String ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int WIDTH = 4, BASE = 34;

    /** One symbol, upper case, case-sensitive. */
    public static final String SYMBOL = "[0-9A-HJ-NP-Z]";
    /** A number inside a new-scheme file name. */
    public static final String CODE = SYMBOL + "{4}";
    /** A number inside a legacy file name; use with CASE_INSENSITIVE where case is not under our control. */
    public static final String LEGACY = "(?:\\d{2}|[A-HJ-NP-Z][0-9A-HJ-NP-Z])";

    static final Pattern VALID = Pattern.compile("^" + CODE + "$"), VALID_LEGACY = Pattern.compile("^" + LEGACY + "$"),
            TYPED = Pattern.compile("^" + SYMBOL + "{1,4}$");

    public static final String FIRST_NUMBER = "0001", LEGACY_FIRST = "01";

    public static boolean valid(String code) {
        return code != null && VALID.matcher(code).matches();
    }

    public static boolean validLegacy(String code) {
        return code != null && VALID_LEGACY.matcher(code).matches();
    }

    public static boolean isLegacy(String code) {
        return code != null && code.length() != WIDTH;
    }

    static String normal(String typed) {
        return typed.strip().toUpperCase().replace('O', '0').replace('I', '1');
    }

    /** What a person typed, as a 4-symbol code: {@code 1}, {@code 01} and {@code 0001} are the same. */
    public static String parse(String typed) {
        if (typed == null) throw new NumberFormatException("not a PRP number: null");
        var s = normal(typed);
        if (!TYPED.matcher(s).matches()) throw new NumberFormatException("not a PRP number: " + typed.strip()
                + " (1 to 4 symbols of 0-9 and A-Z without I and O)");
        return "0".repeat(WIDTH - s.length()) + s;
    }

    /** A typed number in the legacy scheme: a single digit is padded ({@code 4} is {@code 04}). */
    public static String parseLegacy(String typed) {
        if (typed == null) throw new NumberFormatException("not a PRP number: null");
        var s = normal(typed);
        if (s.matches("\\d")) s = "0" + s;
        if (!validLegacy(s)) throw new NumberFormatException("not a PRP number: " + typed.strip()
                + " (two digits 00-99, then A0..ZZ without I and O)");
        return s;
    }

    /** True when what was typed names this PRP number, read in the scheme of the number itself. */
    public static boolean same(String typed, String index) {
        if (typed == null || index == null) return false;
        try {
            return index.equals(isLegacy(index) ? parseLegacy(typed) : parse(typed));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** The value of symbols of any width, upper case. */
    public static int value(String symbols) {
        var n = 0;
        for (var c : symbols.toCharArray()) {
            var d = ALPHABET.indexOf(c);
            if (d < 0) throw new IllegalArgumentException("not base34: " + symbols);
            n = n * BASE + d;
        }
        return n;
    }

    /** {@code n} written in base34, left-padded with 0 to {@code width}. */
    public static String base34(int n, int width) {
        var s = new StringBuilder();
        for (var v = n; v > 0; v /= BASE) s.insert(0, ALPHABET.charAt(v % BASE));
        while (s.length() < width) s.insert(0, '0');
        return s.toString();
    }

    /** The position in its own scheme: {@code 000A} is 10; legacy {@code A0} is 100. */
    public static int ordinal(String code) {
        if (valid(code)) return value(code);
        if (!validLegacy(code)) throw new IllegalArgumentException("not a PRP number: " + code);
        if (Character.isDigit(code.charAt(0))) return Integer.parseInt(code);
        return 100 + LETTERS.indexOf(code.charAt(0)) * BASE + ALPHABET.indexOf(code.charAt(1));
    }

    public static int capacity() {
        return BASE * BASE * BASE * BASE;
    }

    public static int legacyCapacity() {
        return 100 + LETTERS.length() * BASE;
    }

    public static String of(int ordinal) {
        if (ordinal < 0 || ordinal >= capacity()) throw new IllegalArgumentException("no PRP number " + ordinal);
        return base34(ordinal, WIDTH);
    }

    public static String ofLegacy(int ordinal) {
        if (ordinal < 0 || ordinal >= legacyCapacity()) throw new IllegalArgumentException("no PRP number " + ordinal);
        if (ordinal < 100) return String.format("%02d", ordinal);
        var k = ordinal - 100;
        return "" + LETTERS.charAt(k / BASE) + ALPHABET.charAt(k % BASE);
    }

    /** The number after this one in its own scheme, or null after {@code ZZZZ} (legacy {@code ZZ}): it never wraps. */
    public static String next(String code) {
        var n = ordinal(code) + 1;
        if (isLegacy(code)) return n >= legacyCapacity() ? null : ofLegacy(n);
        return n >= capacity() ? null : of(n);
    }

    /** Symbols needed to write the highest number of a project, at least 1. */
    public static int width(String highest) {
        if (highest == null || !valid(highest)) return 1;
        var w = WIDTH;
        while (w > 1 && highest.charAt(WIDTH - w) == '0') w--;
        return w;
    }

    /** Leading zeros stripped down to {@code width} symbols; a legacy code is returned as it is. */
    public static String shortForm(String code, int width) {
        if (!valid(code)) return code;
        var w = Math.max(width(code), Math.max(1, Math.min(WIDTH, width)));
        return code.substring(WIDTH - w);
    }
}
