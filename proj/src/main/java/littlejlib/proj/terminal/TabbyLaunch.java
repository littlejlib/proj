package littlejlib.proj.terminal;

import littlejlib.proj.TerminalLauncher;
import littlejlib.proj.util.Cmd;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Starting something in Tabby, in the only two steps that work: open the tab with the BARE PATH form
 * of its command line, then type the command in over the MCP plugin.
 * <p>
 * Tabby's command verbs - {@code run}, {@code open}, {@code profile} - all crash before doing
 * anything on a current install, and crash silently. The bare path is handled further down its CLI,
 * past the argument parser that dies. Measured in {@code prp/32-prp.01.findings.md}.
 */
public final class TabbyLaunch {

    private TabbyLaunch() { }

    static final long APPEAR_TIMEOUT_MS = 20_000, PROMPT_TIMEOUT_MS = 8_000, POLL_MS = 150,
            ECHO_TIMEOUT_MS = 4_000;
    static final int ATTEMPTS = 3, PROBE_CHARS = 40;

    /** CSI and OSC, so that what a shell has drawn can be told apart from how it drew it. */
    static final Pattern ANSI = Pattern.compile("\\[[0-9;?]*[ -/]*[@-~]|\\][^]*?");

    /** PROJ_TABBY_TRACE=1. This talks to a GUI over a socket; there is nothing else to look at. */
    static final boolean TRACE = System.getenv("PROJ_TABBY_TRACE") != null;

    public static boolean handles(TerminalLauncher t) {
        return t != null && t.getName() != null && t.getName().toLowerCase().contains("tabby");
    }

    /** Opens a tab at this directory. Works whether or not the MCP plugin is installed. */
    public static boolean open(TerminalLauncher tabby, Path directory) {
        var exe = exeOf(tabby);
        return exe != null && Cmd.run(new String[]{exe, directory.toAbsolutePath().toString()}, null);
    }

    public static RunOutcome run(TerminalLauncher tabby, Path directory, String command) {
        var watching = TabbyMcp.available();
        var before = watching ? new HashSet<>(TabbyMcp.sessionIds()) : Set.<String>of();
        trace("mcp=" + watching + " port=" + TabbyMcp.port() + " before=" + before.size());
        if (!open(tabby, directory)) return RunOutcome.FAILED;
        if (!watching) return RunOutcome.OPENED_ONLY;

        var session = awaitNewSession(before);
        trace("session=" + session);
        if (session == null) return RunOutcome.OPENED_ONLY;
        awaitPrompt(session);
        return deliver(session, command);
    }

    /**
     * Typed in, then CHECKED, then typed again if it did not take.
     * <p>
     * A shell that is still starting swallows what is written at it, and the plugin still answers
     * {@code success} because it did write it - so trusting that answer produces the worst outcome
     * this code has: a tab open in the right directory, sitting there having run nothing, reported
     * as a successful launch. Reading the buffer back is the only way to know.
     */
    static RunOutcome deliver(String session, String command) {
        for (var attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (!TabbyMcp.sendInput(session, command + "\r")) {
                trace("send_input refused on attempt " + attempt);
                return RunOutcome.OPENED_ONLY;
            }
            if (awaitEcho(session, command)) {
                trace("delivered on attempt " + attempt);
                return RunOutcome.RAN;
            }
            trace("attempt " + attempt + " did not take");
        }
        return RunOutcome.OPENED_ONLY;
    }

    /**
     * The command showing in the buffer, whitespace ignored - a terminal wraps a long line wherever
     * its width falls, so anything longer than a few words is not contiguous in what it echoes.
     */
    static boolean awaitEcho(String session, String command) {
        var probe = squeeze(command);
        probe = probe.substring(0, Math.min(PROBE_CHARS, probe.length()));
        var deadline = System.currentTimeMillis() + ECHO_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            var buffer = TabbyMcp.buffer(session, 0);
            if (buffer != null && squeeze(strip(buffer)).contains(probe)) return true;
            if (!sleep()) return false;
        }
        return false;
    }

    static String squeeze(String s) {
        return s.replaceAll("\\s+", "");
    }

    /**
     * The tab we just asked for, identified by being the one that was not there before. Tabby hands
     * back no handle, and the newest row is the wrong answer when two launches overlap.
     */
    static String awaitNewSession(Set<String> before) {
        var deadline = System.currentTimeMillis() + APPEAR_TIMEOUT_MS;
        var polls = 0;
        while (System.currentTimeMillis() < deadline) {
            var ids = TabbyMcp.sessionIds();
            polls++;
            for (var id : ids) if (!before.contains(id)) return id;
            if (polls % 20 == 1) trace("poll " + polls + " sees " + ids.size() + " sessions, none new");
            if (!sleep()) return null;
        }
        return null;
    }

    /** A shell still starting up drops what is typed at it, and clink takes about a second. */
    static void awaitPrompt(String session) {
        var deadline = System.currentTimeMillis() + PROMPT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (prompted(TabbyMcp.buffer(session, 6))) return;
            if (!sleep()) return;
        }
        trace("no prompt seen; sending anyway");
    }

    static boolean prompted(String buffer) {
        if (buffer == null || buffer.isBlank()) return false;
        return strip(buffer).stripTrailing().endsWith(">");
    }

    static String strip(String buffer) {
        return ANSI.matcher(buffer).replaceAll("");
    }

    static boolean sleep() {
        try {
            Thread.sleep(POLL_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The executable out of whichever template the settings carry, so a hand-edited path still works. */
    static String exeOf(TerminalLauncher tabby) {
        if (tabby == null || tabby.getOpenCommand() == null) return null;
        var argv = Cmd.parse(tabby.getOpenCommand());
        return argv.length == 0 ? null : argv[0];
    }

    static void trace(String message) {
        if (TRACE) System.err.println("[TabbyLaunch] " + message);
    }
}
