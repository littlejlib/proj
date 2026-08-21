package littlejlib.proj.cct.cli;

import module java.base;

import picocli.CommandLine;
import picocli.CommandLine.*;

/**
 * The transcripts verbs, as one group under proj: {@code proj cct find ...}.
 * <p>
 * Registered inside {@code QueryCommands} rather than on proj's own root, which is what puts it on
 * the daemon route - a query reaches the running daemon when there is one and opens the index
 * itself when there is not, so nothing here has to know which of those happened.
 */
@Command(
    name = "cct",
    mixinStandardHelpOptions = true,
    version = "cct 1.0",
    description = "Find the claude-code conversation you half-remember, by what was said in it.",
    subcommands = {IndexCmd.class, FindCmd.class, ShowCmd.class, ResumeCmd.class, ExportCmd.class},
    synopsisSubcommandLabel = "COMMAND")
public final class CctCli implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.err);
    }

    /** Turns a lock error into the one line that says what to do about it, not a stack trace. */
    public static String message(Throwable e) {
        for (var t = e; t != null; t = t.getCause()) {
            var m = t.getMessage();
            if (m == null) continue;
            if (m.contains("locked") || m.contains("in use") || m.contains("is held by another")
                    || m.contains("Database initialization failed"))
                return locked();
            if (t.getCause() == null) return m;
        }
        return String.valueOf(e);
    }

    /**
     * What to actually DO about a lock error, which is not obvious and was briefly misreported.
     * <p>
     * ArcadeDB's directory lock is held by whichever proj process opened it, and the confusing case
     * is a daemon that is still alive while nothing claims to be running - which happens when the
     * daemon was restarted while a picker window was open, since it cannot exit with a modal window
     * up and the new daemon overwrites the record naming it. The symptom is "no daemon running" and
     * every query failing, so the message has to name that possibility rather than say "locked".
     */
    static String locked() {
        return "the project index is held by another proj process. If a picker window is open, close "
            + "it. If `proj daemon status` says nothing is running and this persists, an earlier "
            + "daemon has been orphaned - find the java process whose window is titled "
            + "'proj - project picker' and end that one.";
    }
}
