package littlejlib.proj.cct.cli;

import module java.base;

/**
 * Where a transcripts command's answer goes.
 * <p>
 * Two destinations, chosen at run time. Standalone it is the real file descriptors, deliberately
 * not {@code System.out}: proj replaces that with a filtering stream that overrides only some
 * methods, so anything reaching it through a Writer vanishes without a trace. Inside the daemon
 * the answer has to become a string that travels back over the socket instead, so a caller may
 * redirect this per thread - concurrent requests each get their own sink.
 */
public final class Out {

    private Out() {}

    private static final PrintStream STDOUT =
        new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private static final PrintStream STDERR =
        new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    private static final ThreadLocal<PrintWriter> OUT = new ThreadLocal<>(), ERR = new ThreadLocal<>();

    public static void redirect(PrintWriter out, PrintWriter err) { OUT.set(out); ERR.set(err); }

    public static void clear() { OUT.remove(); ERR.remove(); }

    public static void line(String s) {
        var w = OUT.get();
        if (w == null) STDOUT.println(s); else { w.println(s); w.flush(); }
    }

    public static void line(String fmt, Object... args) { line(fmt.formatted(args)); }

    public static void blank() { line(""); }

    public static void note(String s) {
        var w = ERR.get();
        if (w == null) STDERR.println(s); else { w.println(s); w.flush(); }
    }

    public static void note(String fmt, Object... args) { note(fmt.formatted(args)); }

    public static String oneLine(String s, int max) {
        if (s == null) return "";
        var flat = s.replaceAll("\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String shortId(String id) {
        return id == null ? "" : (id.length() > 8 ? id.substring(0, 8) : id);
    }
}
