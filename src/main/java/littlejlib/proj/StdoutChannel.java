package littlejlib.proj;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * stdout carries exactly one thing: the resolved path a shell adapter will cd to.
 * Everything a human reads goes to stderr.
 *
 * protect() must run before anything else writes, because it works by moving
 * System.out to stderr. That is what lets the ~500 existing System.out and
 * terminal.writer() call sites keep their code unchanged while a `for /f` wrapper
 * around this process sees a clean, single-line answer on stdout.
 */
public final class StdoutChannel {

    static PrintStream answer;

    public static void protect() {
        if (answer != null) return;
        System.out.flush();
        answer = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    public static void emit(Path p) {
        if (answer == null) protect();
        answer.println(p.toAbsolutePath().normalize());
        answer.flush();
    }

    /**
     * The real stdout, for a command whose ANSWER is its output rather than a path to cd to.
     * <p>
     * protect() reserves stdout for the navigation flow, where a `for /f` wrapper reads exactly one
     * line. That is right for `proj o` and wrong for every query command: the whole point of
     * `proj files --paths` is that it pipes into ripgrep, and a list of paths delivered on stderr
     * pipes into nothing. Those commands are not competing with the adapter - no adapter wraps them
     * - so their answer belongs on stdout, which is where a caller will look for it.
     * <p>
     * This also side-steps LogConfig's noise filter, since it is a direct stream rather than the
     * filtered System.out.
     */
    public static PrintStream data() {
        if (answer == null) protect();
        return answer;
    }

    private StdoutChannel() {}
}
