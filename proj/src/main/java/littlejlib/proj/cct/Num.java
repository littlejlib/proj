package littlejlib.proj.cct;

import module java.base;

public final class Num {

    private Num() {}

    public static long n(Long v) { return v == null ? 0L : v; }

    public static int n(Integer v) { return v == null ? 0 : v; }

    public static boolean n(Boolean v) { return Boolean.TRUE.equals(v); }

    public static String bytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return round(b / 1024.0) + " KB";
        if (b < 1024L * 1024 * 1024) return round(b / (1024.0 * 1024)) + " MB";
        return round(b / (1024.0 * 1024 * 1024)) + " GB";
    }

    public static String count(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1000_000) return round(n / 1000.0) + "k";
        return round(n / 1000_000.0) + "M";
    }

    private static String round(double d) {
        return d >= 100 ? Long.toString(Math.round(d)) : new DecimalFormat("0.#").format(d);
    }

    public static String csv(Collection<String> items) {
        return items.isEmpty() ? null : String.join(",", items);
    }

    public static Set<String> uncsv(String s) {
        var out = new LinkedHashSet<String>();
        if (s == null || s.isBlank()) return out;
        for (var p : s.split(",")) if (!p.isBlank()) out.add(p.trim());
        return out;
    }
}
