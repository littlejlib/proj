package littlejlib.proj.prp;

import littlejlib.proj.cct.export.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * proj's own {@code /export}: the claude-code session you are in, written into the PRP folder under
 * the name the convention says it should have.
 * <p>
 * WHY THIS IS NOT THE SLASH COMMAND. {@code /export prp/29-prp.99.03.logs.txt} is typed by hand,
 * which means the serial number is worked out by hand, which means it is occasionally wrong and
 * always a chore - and claude-code cannot run it for itself because a slash command is the one thing
 * it cannot invoke. Everything in that filename is derivable: the PRP index from the folder, the
 * serial from the logs already there, the session from the working directory. So it is derived.
 * <p>
 * The output is markdown with claude-code's own speaker marks in the headings, and it carries the
 * TOOL CALLS AND THEIR OUTPUT, not only the prose. A log of what Claude said without what Claude
 * did is a summary of the work rather than a record of it - which is the one thing a session log
 * must not be. See {@link SessionLogMd}.
 */
public final class PrpLogExport {

    private PrpLogExport() { }

    public record Written(Path file, Path session, String prpIndex, int turns, long bytes) { }

    /**
     * @param prpDir  the PRP folder to write into
     * @param entry   the PRP this log belongs to
     * @param cwd     the directory whose session should be exported
     * @param session an explicit session file, or null to take the most recent one of {@code cwd}
     * @param resultLines how many lines of each tool result to keep, 0 for all of them
     */
    public static Written write(Path prpDir, PrpEntry entry, Path cwd, Path session, int resultLines) {
        var file = session != null ? session : latestSession(cwd);
        // TWO passes over the file, deliberately. The first is the fast reader, which gives the
        // header its counts without ever materialising a tool result; the second is the detailed one,
        // which materialises everything. Keeping them apart is what stops the export's needs from
        // slowing the indexer down, and a second pass over one file costs milliseconds.
        var stats = SessionRead.of(file, 2).session();
        var entries = TranscriptReader.read(file);
        var name = PrpLogs.nextName(prpDir, entry.getIndex());
        var target = prpDir.resolve(name);
        var text = SessionLogMd.render(stats, entries, heading(entry, prpDir, name, cwd), resultLines);
        try {
            Files.createDirectories(prpDir);
            Files.writeString(target, text, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("could not write " + target + ": " + e.getMessage(), e);
        }
        return new Written(target, file, entry.getIndex(), entries.size(), text.length());
    }

    public static Path latestSession(Path cwd) {
        var found = SessionRead.sessionsOfDirectory(cwd);
        if (found.isEmpty())
            throw new IllegalStateException("no claude-code session recorded for " + cwd
                    + " - if this is a new directory there is nothing to export yet");
        return found.get(0);
    }

    /**
     * The PRP to log against: the one named, else the one THIS SESSION opened on, else the
     * highest-numbered still open, else the highest-numbered at all.
     * <p>
     * The session's own PRP comes before newest-open for a reason worth keeping. A PRP is usually
     * CLOSED at the end of the cycle that finished it, and the log is written moments later - at
     * which point "newest open" is some unrelated PRP from months ago, and the log lands in the wrong
     * place with nothing to warn anybody. What the session was actually about is knowable: its first
     * prompt names it.
     */
    public static PrpEntry pick(List<PrpEntry> all, String index, Path cwd) {
        if (all.isEmpty()) return null;
        var wanted = index == null || index.isBlank() ? sessionPrp(cwd)
                : String.format("%02d", Integer.parseInt(index.trim()));
        if (wanted != null) {
            var named = byIndex(all, wanted);
            // An explicit index that does not exist is an error the caller must see. One we GUESSED
            // from the session is not - fall through to the ordinary default.
            if (named != null || index != null) return named;
        }
        var open = all.stream().filter(p -> p.getStatus() != PrpStatus.CLOSED).toList();
        var pool = open.isEmpty() ? all : open;
        return pool.stream().max((a, b) -> a.getIndex().compareTo(b.getIndex())).orElse(null);
    }

    static PrpEntry byIndex(List<PrpEntry> all, String index) {
        return all.stream().filter(p -> index.equals(p.getIndex())).findFirst().orElse(null);
    }

    /** The PRP index named in the first prompt of this directory's latest session, or null. */
    static String sessionPrp(Path cwd) {
        try {
            return PrpInstructions.openedOn(latestSession(cwd));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static LogHeading heading(PrpEntry entry, Path prpDir, String name, Path cwd) {
        var project = prpDir.getParent() == null ? cwd : prpDir.getParent();
        var serial = name.replaceAll(".*\\.99\\.(\\d+)\\.logs\\.(txt|md)", "$1") + " of PRP " + entry.getIndex();
        var lines = new ArrayList<String>();
        lines.add("prp        " + PrpPrompt.relative(project, entry.getMainFile()));
        if (entry.getStatusFile() != null && Files.exists(entry.getStatusFile()))
            lines.add("status     " + PrpPrompt.relative(project, entry.getStatusFile()));
        var previous = PrpLogs.latest(prpDir, entry.getIndex());
        if (previous != null) lines.add("previous   " + PrpPrompt.relative(project, previous.file()));
        lines.add("");
        return new LogHeading("PRP " + entry.getIndex() + " - " + entry.getName(), serial, lines);
    }
}
