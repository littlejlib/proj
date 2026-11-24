package littlejlib.proj.cli;

import littlejlib.proj.daemon.Client;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * How a command-line query reaches the project index, and how its answer gets back out.
 * <p>
 * THE INDEX is plain files (store/Table) that any process reads without a lock, so a query runs here,
 * never through the daemon.
 * <p>
 * THE OUTPUT, and this is a trap worth knowing about. {@code LogConfig} replaces {@code System.out}
 * with a filtering PrintStream that suppresses third-party log noise, and it overrides only
 * {@code println(String)}, {@code println(Object)}, {@code print(String)} and {@code flush()}.
 * Everything else - in particular {@code write(byte[], int, int)}, which is what a Writer wrapped
 * around that stream ends up calling - falls through to the base PrintStream, whose sink is a
 * ByteArrayOutputStream that nobody ever reads. So {@code System.out.println(x)} prints and
 * {@code new PrintWriter(System.out).println(x)} silently disappears. That cost an hour: the command
 * ran, loaded 2269 records, formatted 705 rows and printed nothing, with exit code 0.
 * <p>
 * Hence: everything is rendered into a StringWriter and handed to {@code System.out.print} at the
 * end, which is an overridden method and therefore actually reaches the console.
 */
public final class IndexAccess {

    private IndexAccess() { }

    static final boolean TRACE = System.getenv("PROJ_CLI_TRACE") != null;

    /**
     * Runs a query here and prints the answer.
     *
     * @return the process exit code
     */
    public static int execute(List<String> args) {
        if (TRACE) System.err.println("[proj] execute " + args);

        var out = new StringWriter();
        var err = new StringWriter();
        var code = QueryCommands.run(args, new PrintWriter(out), new PrintWriter(err));
        print(out.toString(), err.toString());
        return code;
    }

    /** Straight to the real console: not System.out, which is filtered - see the note on this class. */
    static void print(String stdout, String stderr) {
        var out = littlejlib.proj.StdoutChannel.data();
        var err = littlejlib.proj.LogConfig.err();
        if (stdout != null && !stdout.isEmpty()) out.print(stdout);
        if (stderr != null && !stderr.isEmpty()) err.print(stderr);
        out.flush();
        err.flush();
    }

    /** What the daemon calls: same commands, output captured into the response. */
    public static String serveInDaemon(List<String> args, StringWriter errSink) {
        var sink = new StringWriter();
        QueryCommands.run(args, new PrintWriter(sink), new PrintWriter(errSink));
        return sink.toString();
    }
}
