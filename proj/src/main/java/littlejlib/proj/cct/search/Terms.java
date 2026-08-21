package littlejlib.proj.cct.search;

import module java.base;

public final class Terms {

    private final List<String> terms;
    private final boolean caseSensitive;

    private Terms(List<String> terms, boolean caseSensitive) {
        this.terms = terms;
        this.caseSensitive = caseSensitive;
    }

    public static Terms of(String query, MatchMode mode) {
        var q = query == null ? "" : query.strip();
        var resolved = mode.resolve(q);
        if (q.isEmpty()) return new Terms(List.of(), false);
        return switch (resolved) {
            case PHRASE -> new Terms(List.of(q), true);
            case KEYWORDS -> new Terms(split(q), false);
            default -> new Terms(List.of(q), false);
        };
    }

    private static List<String> split(String q) {
        var out = new ArrayList<String>();
        for (var p : q.split("\\s+")) if (!p.isBlank()) out.add(p);
        return out.isEmpty() ? List.of(q) : out;
    }

    public boolean isEmpty() { return terms.isEmpty(); }

    public List<String> all() { return terms; }

    public String longest() {
        var best = "";
        for (var t : terms) if (t.length() > best.length()) best = t;
        return best;
    }

    public String sqlPrefilter() {
        return "%" + longest().replace('%', '_') + "%";
    }

    public boolean matches(String text) {
        if (text == null) return false;
        if (terms.isEmpty()) return true;
        var hay = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
        for (var t : terms) {
            var needle = caseSensitive ? t : t.toLowerCase(Locale.ROOT);
            if (!hay.contains(needle)) return false;
        }
        return true;
    }

    public int indexIn(String text) {
        if (text == null || terms.isEmpty()) return -1;
        var hay = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
        var best = -1;
        for (var t : terms) {
            var i = hay.indexOf(caseSensitive ? t : t.toLowerCase(Locale.ROOT));
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    public String snippet(String text, int radius) {
        if (text == null) return "";
        var at = indexIn(text);
        var from = at < 0 ? 0 : Math.max(0, at - radius);
        var to = Math.min(text.length(), (at < 0 ? 0 : at) + radius);
        var cut = text.substring(from, to).replaceAll("\\s+", " ").strip();
        return (from > 0 ? "…" : "") + cut + (to < text.length() ? "…" : "");
    }
}
