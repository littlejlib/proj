package littlejlib.proj.prp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The exported claude-code session logs of a PRP: {@code NN-prp.99.SS.logs.txt}.
 * <p>
 * They are not sub-files as {@link PrpScanner} counts them - that only looks at {@code .md} - and
 * they should not be, because they are not part of the specification. They matter for exactly one
 * thing: a new session on an old PRP has to be told where the last one left off, and the answer is
 * the tail of the highest-numbered log.
 * <p>
 * 99 is the convention's marker for "this is a log, not a numbered sub-file", so a PRP can have
 * sub-files 01..98 and logs 99.01..99.NN without the two ever colliding.
 */
public final class PrpLogs {

    private PrpLogs() { }

    /**
     * Both extensions. {@code /export} wrote {@code .txt} and every log before 2026-08-21 is one;
     * {@code proj prp logs} writes {@code .md}, because with {@code ##} headings the editor's outline
     * becomes the index. A reader that only knew one of them would tell a new session there was no
     * previous cycle.
     */
    static final Pattern LOG = Pattern.compile("(\\d{2})-prp\\.99\\.(\\d+)\\.logs\\.(txt|md)",
            Pattern.CASE_INSENSITIVE);

    public record Session(int index, Path file) { }

    /** Every session log of one PRP, oldest first. Empty when the PRP has never been worked on. */
    public static List<Session> sessionsOf(Path prpDir, String prpIndex) {
        var out = new ArrayList<Session>();
        if (prpDir == null || !Files.isDirectory(prpDir)) return out;
        try (var files = Files.list(prpDir)) {
            for (var f : files.toList()) {
                var m = LOG.matcher(f.getFileName().toString());
                if (!m.matches() || !m.group(1).equals(prpIndex)) continue;
                out.add(new Session(Integer.parseInt(m.group(2)), f));
            }
        } catch (Exception e) {
            // an unreadable prp folder is an empty log list, not a failed launch
        }
        out.sort(Comparator.comparingInt(Session::index));
        return out;
    }

    /** The session a new one continues from, or null when there is none to continue. */
    public static Session latest(Path prpDir, String prpIndex) {
        var all = sessionsOf(prpDir, prpIndex);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    /** What the NEXT export should be called. Markdown now; the old ones stay readable as they are. */
    public static String nextName(Path prpDir, String prpIndex) {
        var last = latest(prpDir, prpIndex);
        return String.format("%s-prp.99.%02d.logs.md", prpIndex, last == null ? 1 : last.index() + 1);
    }
}
