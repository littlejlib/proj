package littlejlib.proj.prp;

import littlejlib.proj.cct.export.Stamp;
import littlejlib.proj.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Putting a block INTO a PRP without disturbing what is already there.
 * <p>
 * The convention says a PRP is read-only to an agent, and that rule is not being repealed here: this
 * is a command the USER runs, it only ever INSERTS, it never rewrites a line it did not write, and it
 * keeps a copy of the previous file before touching it. What it inserts is delimited on both sides so
 * the next run can find its own work and leave everyone else's alone.
 * <p>
 * CHRONOLOGICAL, not appended. Blocks carry the {@code from} time of the cycle they came from, and a
 * new one is placed among them by that time rather than at the end - so a PRP read top to bottom
 * tells the story in the order it happened, which is the only order that makes a series of
 * corrections comprehensible. Everything that is not one of these blocks stays exactly where it is.
 */
public final class PrpFileEdit {

    private PrpFileEdit() { }

    static final Pattern OPEN = Pattern.compile(
            "^<user-instructions\\s+([^>]*)>\\s*$", Pattern.MULTILINE);

    static final Pattern CLOSE = Pattern.compile("^</user-instructions>\\s*$", Pattern.MULTILINE);

    static final Pattern ATTR = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    public record Block(int start, int end, String session, String from) { }

    public record Result(String text, String what) { }

    /** Every block this tool has previously written, in the order they appear in the file. */
    public static List<Block> blocksIn(String text) {
        var out = new ArrayList<Block>();
        var open = OPEN.matcher(text);
        while (open.find()) {
            var close = CLOSE.matcher(text);
            if (!close.find(open.end())) break;
            out.add(new Block(open.start(), close.end(), attr(open.group(1), "session"),
                    attr(open.group(1), "from")));
        }
        return out;
    }

    /**
     * The PRP with our own blocks removed.
     * <p>
     * This is what "is it already in the PRP?" has to be asked of, and getting it wrong once was
     * instructive: on the second run the tool found its OWN eight instructions in the file, decided
     * they were already there, and replaced the block with the single newest one. A collector that
     * eats its own output is worse than no collector.
     */
    public static String withoutBlocks(String text) {
        var out = new StringBuilder();
        var at = 0;
        for (var b : blocksIn(text)) {
            out.append(text, at, b.start());
            at = b.end();
        }
        return out.append(text.substring(at)).toString();
    }

    static String attr(String attrs, String name) {
        var m = ATTR.matcher(attrs);
        while (m.find()) if (name.equals(m.group(1))) return m.group(2);
        return "";
    }

    /**
     * @param block the whole block, its own open and close tags included
     * @param from  the cycle's start time, used to place it
     * @param session the short session id, used to recognise a block this tool already wrote
     */
    public static Result insert(String text, String block, String from, String session) {
        var body = block.strip();
        var existing = blocksIn(text);
        for (var b : existing) {
            if (!session.equals(b.session())) continue;
            // Our own block for this same cycle. Replaced, because the cycle has continued since it
            // was written and a second copy of a growing list is not history, it is noise.
            return new Result(text.substring(0, b.start()) + body + text.substring(b.end()),
                    "replaced the existing block for session " + session);
        }
        for (var b : existing) {
            if (later(b.from(), from)) continue;
            return new Result(text.substring(0, b.start()) + body + "\n\n" + text.substring(b.start()),
                    "inserted before the block from " + b.from());
        }
        // A blank line between the PRP and the block, always: markdown would otherwise join the
        // last sentence to the tag, and an inserted section must never restyle the document it
        // was inserted into.
        return new Result(text.stripTrailing() + "\n\n" + body + "\n", existing.isEmpty()
                ? "appended as the first collected block" : "appended after the last block");
    }

    /** True when {@code a} is at or before {@code b}. Both are {@code yyyy-MM-dd HH:mm ZONE}. */
    static boolean later(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) return true;
        // The date and clock sort lexically; the zone suffix is the same file to file, and when it
        // is not, an hour of imprecision in the ORDER of two cycles is not worth a parser.
        return a.compareTo(b) <= 0;
    }

    /**
     * The previous content, kept before the file is touched. Cheap, invisible, and the reason a bad
     * insert into a document the user has been writing all week is recoverable rather than a story.
     */
    public static Path backup(Path prpFile) throws IOException {
        var dir = AppPaths.home().resolve("prp-backups");
        Files.createDirectories(dir);
        var stamp = Stamp.now().replaceAll("[^0-9]", "");
        var target = dir.resolve(prpFile.getFileName() + "." + stamp + ".bak");
        Files.copy(prpFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static void write(Path prpFile, String text) throws IOException {
        Files.writeString(prpFile, text, StandardCharsets.UTF_8);
    }
}
