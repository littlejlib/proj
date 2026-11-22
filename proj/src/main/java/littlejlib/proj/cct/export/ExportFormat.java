package littlejlib.proj.cct.export;

public enum ExportFormat {

    MARKDOWN("md"),
    TEXT("txt"),
    XHTML("xhtml"),
    JSONL("jsonl");

    public final String extension;

    ExportFormat(String extension) { this.extension = extension; }

    public static ExportFormat of(String s) {
        if (s == null || s.isBlank()) return MARKDOWN;
        for (var f : values()) if (f.name().equalsIgnoreCase(s) || f.extension.equalsIgnoreCase(s)) return f;
        throw new IllegalArgumentException("unknown export format '" + s
            + "' - one of md, txt, xhtml, jsonl");
    }
}
