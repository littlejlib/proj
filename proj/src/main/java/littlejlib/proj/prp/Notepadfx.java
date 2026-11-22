package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The command line of notepadfx, for one purpose (PRP 001R): a PRP file the person has open must be changed
 * through the editor, never on disk, or the editor loses his unsaved typing or reports "modified externally".
 * <p>
 * {@code status} exits 0 for a file that is open and 3 for one that is not (2: notepadfx is not running). Only 0 means
 * open; a missing program, a timeout or any other answer is "not open", so proj without notepadfx writes on disk as it
 * always did. Exit 5 from a change means the text moved meanwhile and nothing changed.
 */
public final class Notepadfx {

    private Notepadfx() { }

    public static final int OK = 0, STALE = 5;

    /** Exit code and standard output of one call; exit -1 when the program could not run. */
    public record Out(int exit, String text) { }

    static final long TIMEOUT_SECONDS = 15;

    static Path exe() {
        var found = AgentExe.resolve("notepadfx");
        if (found != null) return found;
        var local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            var f = Path.of(local, "Programs", "notepadfx", "notepadfx.exe");
            if (Files.isRegularFile(f)) return f;
        }
        return null;
    }

    public static Out run(List<String> args) {
        var exe = exe();
        if (exe == null) return new Out(-1, "");
        var cmd = new ArrayList<String>();
        cmd.add(exe.toString());
        cmd.addAll(args);
        try {
            var p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(nullFile())).start();
            var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Out(-1, out);
            }
            return new Out(p.exitValue(), out);
        } catch (IOException e) {
            return new Out(-1, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Out(-1, "");
        }
    }

    static java.io.File nullFile() {
        return new java.io.File(System.getProperty("os.name", "").toLowerCase().contains("win") ? "NUL" : "/dev/null");
    }

    public static boolean isOpen(Path file) {
        return run(List.of("status", file.toAbsolutePath().toString())).exit() == OK;
    }

    /** The live text of lines {@code from}..{@code to} (what the person sees, saved or not), or null when it cannot be read. */
    public static List<String> readLines(Path file, int from, int to) {
        var r = run(List.of("read", file.toAbsolutePath().toString(), "--lines", from + "-" + to));
        if (r.exit() != OK) return null;
        var all = List.of(r.text().split("\r?\n", -1));
        var sep = all.indexOf("---");          // the answer's own header ends at the first ---; the text follows
        if (sep < 0) return null;
        var text = new ArrayList<>(all.subList(sep + 1, all.size()));
        while (!text.isEmpty() && text.get(text.size() - 1).isEmpty() && text.size() > to - from + 1) text.remove(text.size() - 1);
        return text;
    }

    /** Replaces one line, expecting its current text. Returns the exit code ({@link #OK}, {@link #STALE}, ...). */
    public static int replaceLine(Path file, int line, String expect, String text) {
        return withFiles(expect, text, (e, t) -> run(List.of("replace", file.toAbsolutePath().toString(), "--lines", line + "-" + line,
                "--expect-file", e.toString(), "--text-file", t.toString())).exit());
    }

    /** Inserts one line after line {@code after}, expecting that line's current text. */
    public static int insertAfter(Path file, int after, String expect, String text) {
        return withFiles(expect, text, (e, t) -> run(List.of("insert", file.toAbsolutePath().toString(), "--after-line", String.valueOf(after),
                "--expect-file", e.toString(), "--text-file", t.toString())).exit());
    }

    /**
     * The expected and the new text go in files, never on the command line: Windows argument quoting strips the double
     * quotes of a value such as {@code created_utc: "2026-11-22T06:35:00Z"}, and YAML then reads it as a timestamp.
     */
    static int withFiles(String expect, String text, java.util.function.BiFunction<Path, Path, Integer> call) {
        Path e = null, t = null;
        try {
            e = Files.createTempFile("proj-nfx-expect", ".txt");
            t = Files.createTempFile("proj-nfx-text", ".txt");
            Files.writeString(e, expect, StandardCharsets.UTF_8);
            Files.writeString(t, text, StandardCharsets.UTF_8);
            return call.apply(e, t);
        } catch (IOException ex) {
            return -1;
        } finally {
            try {
                if (e != null) Files.deleteIfExists(e);
                if (t != null) Files.deleteIfExists(t);
            } catch (IOException ignored) {
                // a temp file left behind is harmless
            }
        }
    }
}
