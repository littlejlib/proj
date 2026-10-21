package littlejlib.proj;

import littlejlib.proj.terminal.ConsoleInput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Asking a person a question on the terminal. All that is left of what JLine was here for.
 * <p>
 * proj carried a whole terminal library to do three things: print, read a line, and read one key.
 * Printing is {@code System.out} - already pointed at stderr by {@link StdoutChannel}, which is
 * where human-facing bytes belong. A line is a {@link BufferedReader}. One key is
 * {@link ConsoleInput#readKey()}. The library also started a thread that drained the console input
 * queue, which is why it is gone - see prp/32-prp.01.findings.md section 7. Removing it bought correctness
 * rather than only lines.
 * <p>
 * The prompt is printed rather than handed to the reader, because the two streams are not the same
 * place: a person watches stderr while a shell adapter reads stdout.
 */
public final class Prompt {

    private Prompt() { }

    /** ESC, as the three menus that read single keys have always reported it. */
    public static final String ESCAPE = String.valueOf((char) 27);

    static BufferedReader in;

    /** A line, with a prompt. Empty string at end of input - never null, since callers trim it. */
    public static String ask(String prompt) {
        if (prompt != null && !prompt.isEmpty()) {
            System.out.print(prompt);
            System.out.flush();
        }
        return ask();
    }

    public static String ask() {
        try {
            if (in == null) in = new BufferedReader(new InputStreamReader(System.in));
            var line = in.readLine();
            return line == null ? "" : line;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * One keypress, not echoed and needing no Return. {@link #ESCAPE} for Escape, and an empty
     * string when there is no console to read - which a caller treats as "nothing chosen" rather
     * than blocking forever on a stream that will never deliver.
     */
    public static String key() {
        var c = ConsoleInput.readKey();
        if (c < 0) return "";
        if (c == 27) return ESCAPE;
        return String.valueOf((char) c);
    }
}
