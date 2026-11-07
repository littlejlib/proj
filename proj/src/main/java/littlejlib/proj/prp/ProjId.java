package littlejlib.proj.prp;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.UUID;

/** A random 128-bit id written in base26 with lower case letters, 28 of them. */
public final class ProjId {

    private ProjId() { }

    static final int LENGTH = 28;

    public static String mint() {
        var u = UUID.randomUUID();
        var n = new BigInteger(1, ByteBuffer.allocate(16).putLong(u.getMostSignificantBits())
                .putLong(u.getLeastSignificantBits()).array());
        var out = new char[LENGTH];
        var base = BigInteger.valueOf(26);
        for (var i = LENGTH - 1; i >= 0; i--) {
            var d = n.divideAndRemainder(base);
            out[i] = (char) ('a' + d[1].intValue());
            n = d[0];
        }
        return new String(out);
    }

    public static boolean valid(String s) {
        return s != null && s.matches("[a-z]{" + LENGTH + "}");
    }
}
