package littlejlib.proj.track;

import java.io.PrintStream;

/**
 * Prints lines until a token budget (characters / 4) is spent, then counts what it left out, so a view
 * read by an agent can never cost more than it was given. A budget of 0 prints everything.
 */
public final class Budget {

    final PrintStream out;
    final long maxChars;
    long used;
    int left;

    public Budget(PrintStream out, long tokens) {
        this.out = out;
        this.maxChars = tokens <= 0 ? 0 : tokens * 4;
    }

    public void line(String s) {
        if (maxChars > 0 && used + s.length() + 1 > maxChars) {
            left++;
            return;
        }
        out.println(s);
        used += s.length() + 1;
    }

    public void end() {
        if (left > 0) out.println("(" + left + " more lines left out by --budget)");
    }

    public static String cut(String s, int max) {
        if (s == null) return "";
        var one = s.replaceAll("\\s*\\R\\s*", " ");
        return one.length() <= max ? one : one.substring(0, max - 3) + "...";
    }
}
