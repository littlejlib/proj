package littlejlib.proj.cct.action;

import module java.base;

/**
 * How to say "go there, then run this" in each shell the user actually has open.
 * <p>
 * This is not cosmetic. In cmd.exe {@code cd} does NOT change drive - {@code cd "G:\..."} typed at a
 * C: prompt silently succeeds and leaves you on C:, which is the "wrong, and not an error" failure
 * the store itself is full of warnings about. This machine's sessions span C: and G:, so the cmd
 * form needs {@code /d} or the command quietly resumes in the wrong directory. Windows PowerShell
 * 5.1 has no {@code &&} at all, so there {@code ;} is the only chain available.
 */
public enum ShellKind {

    CMD("cmd", " & "),
    CMD_STRICT("cmd&&", " && "),
    POWERSHELL("powershell", " ; "),
    BASH("bash", " && ");

    public final String label, join;

    ShellKind(String label, String join) { this.label = label; this.join = join; }

    public static ShellKind of(String s) {
        if (s == null || s.isBlank()) return preferred();
        for (var k : values()) if (k.label.equalsIgnoreCase(s) || k.name().equalsIgnoreCase(s)) return k;
        throw new IllegalArgumentException("unknown shell '" + s + "' - one of cmd, cmd&&, powershell, bash");
    }

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static ShellKind preferred() { return windows() ? CMD : BASH; }

    public ShellKind next() { return values()[(ordinal() + 1) % values().length]; }

    public String cd(String dir) {
        return switch (this) {
            case CMD, CMD_STRICT -> "cd /d \"" + dir + "\"";
            case POWERSHELL -> "cd \"" + dir + "\"";
            case BASH -> "cd \"" + posix(dir) + "\"";
        };
    }

    /** A windows path as git-bash sees it: drive letter lowercased and moved in front of a slash. */
    static String posix(String dir) {
        var d = dir.replace('\\', '/');
        if (d.length() > 2 && d.charAt(1) == ':')
            return "/" + Character.toLowerCase(d.charAt(0)) + d.substring(2);
        return d;
    }
}
