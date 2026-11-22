package littlejlib.proj.cct.export;

import module java.base;

import littlejlib.proj.cct.model.SessionDoc;

public final class ExportNames {

    private ExportNames() {}

    /**
     * A filename that says which conversation this is without being opened - the title, then the
     * short session id so two sessions on the same subject stay apart.
     */
    public static String base(SessionDoc s) {
        var title = s.aiTitle() == null || s.aiTitle().isBlank() ? "untitled-session" : s.aiTitle();
        var id = s.sessionId() == null ? "" : s.sessionId();
        return safe(title) + "-" + (id.length() > 8 ? id.substring(0, 8) : id);
    }

    public static String safe(String s) {
        var flat = s.replaceAll("\s+", " ").strip();
        var cleaned = flat.replaceAll("[^A-Za-z0-9 ._-]", "").replace(' ', '_');
        if (cleaned.length() > 90) cleaned = cleaned.substring(0, 90);
        return cleaned.isBlank() ? "session" : cleaned;
    }

    public static Path resolve(Path dir, SessionDoc s, ExportFormat format) {
        return dir.resolve(base(s) + "." + format.extension);
    }
}
