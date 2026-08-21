package littlejlib.proj.cct.search;

public enum MatchMode {

    AUTO, SUBSTRING, PHRASE, KEYWORDS;

    public static MatchMode of(String s) {
        if (s == null || s.isBlank()) return AUTO;
        for (var m : values()) if (m.name().equalsIgnoreCase(s)) return m;
        return AUTO;
    }

    public MatchMode resolve(String query) {
        if (this != AUTO) return this;
        return query != null && query.strip().contains(" ") ? KEYWORDS : SUBSTRING;
    }
}
