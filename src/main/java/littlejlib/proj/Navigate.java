package littlejlib.proj;

import java.nio.file.Path;

/**
 * A child process cannot change its parent's working directory, so the shell has to do
 * it and this only decides how the answer is handed over.
 *
 * Clink settles what "clean" means here. It runs inside cmd.exe with ReadConsoleW
 * hooked, and its own directory shortcuts still work by rewriting the input line into
 * a cd that cmd.exe executes -- see make_cd_command in clink/lib/src/intercept.cpp.
 * Nothing is injected into another process, and neither does this.
 */
public final class Navigate {

    static boolean adapterReading = false;

    public static void adapterReading(boolean v) {
        adapterReading = v;
    }

    /**
     * Decision table, in order. Each case is distinguished by something observable, because
     * guessing wrong is worse than either branch: printing a path nobody reads looks like a
     * silent failure, and opening a window when a shell was waiting is just wrong.
     *
     *   1. adapter asked (--print-cd)  -> stdout, nothing else
     *   2. stdout is a pipe or file    -> stdout; a script or agent is capturing it
     *   3. wrapper set PROJ_POSTRUN_*  -> postrun script, so projold.bat keeps working
     *   4. console attached            -> stdout, plus clipboard and a one-time hint
     *   5. no console at all           -> no shell to talk to; open a terminal there
     */
    public static void to(Path dir) {
        var target = dir.toAbsolutePath().normalize();

        if (adapterReading || LaunchMode.stdoutCaptured()) {
            StdoutChannel.emit(target);
            return;
        }

        if (TerminalHelper.isPostrunBatchAvailable() && TerminalHelper.writePostrunBatch(target)) {
            note("Directory change scheduled (projold.bat wrapper): " + target);
            return;
        }

        if (!LaunchMode.hasConsole()) {
            note("No console attached; opening a terminal at " + target);
            if (!ProjectManager.openTerminalAt(target)) note("Could not open a terminal at " + target);
            return;
        }

        StdoutChannel.emit(target);
        var cmd = Shell.cdCommand(target);
        if (Clipboard.copy(cmd)) note("Copied to clipboard: " + cmd);
        else note("Run: " + cmd);
        note("To make this automatic:  proj --init " + initNameFor(Shell.detect()));
    }

    static String initNameFor(String shell) {
        return shell.equals("unknown") ? "cmd" : shell;
    }

    static void note(String s) {
        System.err.println(s);
    }

    private Navigate() {}
}
