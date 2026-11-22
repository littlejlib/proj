package littlejlib.proj.cct.ui;

import module java.base;

import littlejlib.proj.cct.search.SearchHit;

public final class Snippets {

    private Snippets() {}

    public static String oneLine(String s, int max) {
        if (s == null) return "";
        var flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String matchLabel(SearchHit h) {
        var parts = new ArrayList<String>();
        if (h.cwdMatch) parts.add("path");
        if (h.titleMatch) parts.add("title");
        if (h.promptMatch) parts.add("prompt");
        if (h.humanMatches > 0) parts.add(h.humanMatches + " human");
        if (h.assistantMatches > 0) parts.add(h.assistantMatches + " assistant");
        if (h.thinkingMatches > 0) parts.add(h.thinkingMatches + " thinking");
        return String.join(", ", parts);
    }
}
