package littlejlib.proj.track;

/** The claude-code session a write belongs to: the one asked for, else the one this process runs in. */
public final class Session {

    private Session() { }

    public static String current(String asked) {
        if (asked != null && !asked.isBlank()) return asked.strip();
        var env = System.getenv("CLAUDE_CODE_SESSION_ID");
        return env == null || env.isBlank() ? null : env.strip();
    }

    public static String shortId(String session) {
        return session == null ? "-" : session.length() > 8 ? session.substring(0, 8) : session;
    }
}
