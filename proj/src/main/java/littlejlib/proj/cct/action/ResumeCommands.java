package littlejlib.proj.cct.action;

import module java.base;

import littlejlib.proj.cct.model.SessionDoc;

public final class ResumeCommands {

    private ResumeCommands() {}

    public static final String SKIP = "--dangerously-skip-permissions";

    public static String bare(SessionDoc s) { return "claude --resume " + s.sessionId(); }

    public static String forThisPlatform(SessionDoc s) { return in(ShellKind.preferred(), s, false, false); }

    public static String in(ShellKind shell, SessionDoc s, boolean skip, boolean fork) {
        var tail = new StringBuilder("claude --resume ").append(s.sessionId());
        if (fork) tail.append(" --fork-session");
        if (skip) tail.append(' ').append(SKIP);
        var cwd = s.cwd();
        if (cwd == null || cwd.isBlank()) return tail.toString();
        return shell.cd(cwd) + shell.join + tail;
    }

    /**
     * Everything about getting back in, in one paste: what the session was, where it was, and the
     * command in every shell the user runs, because the answer differs per shell in a way that
     * fails silently rather than loudly.
     */
    public static String block(SessionDoc s) {
        var lines = new ArrayList<String>();
        lines.add("# " + (s.aiTitle() == null || s.aiTitle().isBlank() ? "(untitled session)" : s.aiTitle()));
        lines.add("# session " + s.sessionId());
        if (s.cwd() != null && !s.cwd().isBlank()) lines.add("# folder  " + s.cwd());
        var note = note(s);
        if (!note.isBlank()) lines.add("# note    " + note);
        lines.add("# cmd.exe (& always runs claude; && only if the cd succeeded)");
        lines.add(in(ShellKind.CMD, s, false, false));
        lines.add(in(ShellKind.CMD_STRICT, s, false, false));
        lines.add("# powershell");
        lines.add(in(ShellKind.POWERSHELL, s, false, false));
        lines.add("# git bash");
        lines.add(in(ShellKind.BASH, s, false, false));
        lines.add("# skipping permission prompts");
        lines.add(in(ShellKind.preferred(), s, true, false));
        return String.join("\n", lines);
    }

    public static boolean resumable(SessionDoc s) {
        return s != null && s.sessionId() != null
            && !Boolean.TRUE.equals(s.subagent())
            && !Boolean.TRUE.equals(s.transcriptMissing());
    }

    public static String note(SessionDoc s) {
        if (s == null) return "";
        if (Boolean.TRUE.equals(s.transcriptMissing()))
            return "transcript pruned by claude-code - only the prompts you typed survive, so --resume will likely not find it; the folder is still correct";
        if (Boolean.TRUE.equals(s.subagent()))
            return "subagent transcript - not directly resumable; resume the parent " + s.parentSessionId();
        if (s.cwd() == null || s.cwd().isBlank())
            return "no folder recorded - resume works from anywhere but adopts whatever directory you run it in";
        return "";
    }
}
