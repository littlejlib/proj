package littlejlib.proj.self;

/** Orders versions like 0.2.0 and 0.3.0-beta.1: the numbers first, a version with a label before the same without. */
public final class Versions {
    private Versions() { }

    public static int compare(String a, String b) {
        var x = a.split("-", 2);
        var y = b.split("-", 2);
        var core = compare(x[0].split("\\."), y[0].split("\\."));
        if (core != 0) return core;
        if (x.length != y.length) return x.length == 1 ? 1 : -1;
        return x.length == 1 ? 0 : compare(x[1].split("\\."), y[1].split("\\."));
    }

    static int compare(String[] a, String[] b) {
        for (var i = 0; i < Math.max(a.length, b.length); i++) {
            if (i >= a.length) return -1;
            if (i >= b.length) return 1;
            var c = part(a[i], b[i]);
            if (c != 0) return c;
        }
        return 0;
    }

    static int part(String a, String b) {
        var na = a.chars().allMatch(Character::isDigit) && !a.isEmpty();
        var nb = b.chars().allMatch(Character::isDigit) && !b.isEmpty();
        if (na && nb) return Long.compare(Long.parseLong(a), Long.parseLong(b));
        if (na != nb) return na ? -1 : 1;
        return a.compareTo(b);
    }
}
