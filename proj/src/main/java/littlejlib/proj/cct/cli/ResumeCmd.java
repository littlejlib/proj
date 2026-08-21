package littlejlib.proj.cct.cli;

import module java.base;

import picocli.CommandLine.*;
import littlejlib.proj.cct.action.*;
import littlejlib.proj.cct.db.Db;

@Command(name = "resume", description = "Print the command that gets you back into a session.")
public final class ResumeCmd implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "SESSION", description = "Session id or any unambiguous prefix.")
    String session;

    @Option(names = "--shell",
        description = "cmd (the default on windows), cmd&&, powershell, or bash. cmd matters: plain "
            + "cd does not change drive, so only the /d form actually lands in the folder.")
    String shell;

    @Option(names = "--fork", description = "Resume into a new session id, leaving the original untouched.")
    boolean fork;

    @Option(names = {"-y", "--skip-permissions"}, description = "Add --dangerously-skip-permissions to the command.")
    boolean skipPermissions;

    @Option(names = "--block", description = "Print a pasteable block: title, id, folder and both commands.")
    boolean block;

    @Option(names = "--bare", description = "Just the claude --resume line, without the cd.")
    boolean bare;

    @Override
    public Integer call() {
        var db = Db.get();
        try {
            var found = Sessions.byPrefix(db.db(), session);
            if (found.isEmpty()) { Out.note("no session matches " + session); return 1; }
            if (found.size() > 1) {
                Out.note("%d sessions start with that prefix; be more specific", found.size());
                for (var s : found) Out.note("  %s  %s", s.sessionId(), Out.oneLine(s.aiTitle(), 60));
                return 2;
            }
            var s = found.get(0);
            var note = ResumeCommands.note(s);
            if (!note.isBlank()) Out.note("note: " + note);
            Out.line(command(s));
            return 0;
        } finally {
            db.shutdown();
        }
    }

    private String command(littlejlib.proj.cct.model.SessionDoc s) {
        if (block) return ResumeCommands.block(s);
        if (bare) return ResumeCommands.bare(s);
        var kind = littlejlib.proj.cct.action.ShellKind.of(shell);
        return ResumeCommands.in(kind, s, skipPermissions, fork);
    }
}
