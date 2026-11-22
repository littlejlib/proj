package littlejlib.proj.prp;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * The id alphabet of proj: 0-9 and a-z without i and o (34 symbols), written lower case, read in any
 * case with {@code o} as 0 and {@code i} as 1, exactly as {@link PrpNumber} reads a number.
 * Twenty-six symbols hold 128 bits (34^26 is about 2^132).
 */
public final class Base34 {

    private Base34() { }

    public static final String ALPHABET = "0123456789abcdefghjklmnpqrstuvwxyz";

    public static final int LENGTH = 26;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length());

    public static String mint() {
        var n = new BigInteger(128, RANDOM);
        var out = new char[LENGTH];
        for (var i = LENGTH - 1; i >= 0; i--) {
            var d = n.divideAndRemainder(BASE);
            out[i] = ALPHABET.charAt(d[1].intValue());
            n = d[0];
        }
        return new String(out);
    }

    /** What a person typed, as written form: stripped, lower case, o to 0, i to 1. Not checked. */
    public static String normalize(String typed) {
        return typed == null ? "" : typed.strip().toLowerCase().replace('o', '0').replace('i', '1');
    }

    /** True for a whole id in written form. */
    public static boolean valid(String s) {
        if (s == null || s.length() != LENGTH) return false;
        for (var i = 0; i < s.length(); i++) if (ALPHABET.indexOf(s.charAt(i)) < 0) return false;
        return true;
    }

    /** True for 1 or more symbols of the alphabet: a start of an id, as typed. */
    public static boolean validPrefix(String s) {
        var n = normalize(s);
        if (n.isEmpty() || n.length() > LENGTH) return false;
        for (var i = 0; i < n.length(); i++) if (ALPHABET.indexOf(n.charAt(i)) < 0) return false;
        return true;
    }
}
