package littlejlib.proj.migrate;

import module java.base;

import littlejlib.proj.prp.PrpNumber;

/** Base34 numbers and the short form used in tab and session names (leading zeros down to the project's width). */
final class ShortNumber {

    private ShortNumber() { }

    static final String DIGITS = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    static long value(String number) {
        var v = 0L;
        for (var c : number.strip().toUpperCase(Locale.ROOT).replace('O', '0').replace('I', '1').toCharArray()) {
            var d = DIGITS.indexOf(c);
            if (d < 0) throw new IllegalArgumentException("not a PRP number: " + number);
            v = v * DIGITS.length() + d;
        }
        return v;
    }

    static String stripped(String number) {
        return PrpNumber.shortForm(PrpNumber.parse(number), 1);
    }

    /** Symbols needed to write the highest of these numbers, at least 1. */
    static int width(Collection<String> numbers) {
        return numbers.stream().mapToInt(n -> PrpNumber.width(PrpNumber.parse(n))).max().orElse(1);
    }

    static String of(String number, int width) {
        return PrpNumber.shortForm(PrpNumber.parse(number), width);
    }
}
