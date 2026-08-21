package littlejlib.proj.prp;

import littlejlib.proj.cct.action.PrpLink;
import littlejlib.proj.cct.export.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the user told the agent that is NOT in the PRP.
 * <p>
 * A PRP is written once and then argued with for hours. The arguing happens in the conversation -
 * "actually make it white", "you missed the tool output", "F11 conflicts" - and those instructions
 * are as binding as the document, but they live in a transcript nobody will read again. When the
 * next cycle starts, the agent is handed the specification and none of the corrections, and it
 * cheerfully rebuilds what was already rejected.
 * <p>
 * So they are collected and put back into the PRP, where the next cycle will actually see them.
 * <p>
 * ALREADY-IN-THE-PRP IS THE FILTER THAT MATTERS. The user pastes the important ones in by hand as
 * they go, and a tool that appends those again turns the document into an echo. Anything whose text
 * is already in the file is skipped, so running this repeatedly converges instead of growing.
 */
public final class PrpInstructions {

    private PrpInstructions() { }

    public record Found(List<TranscriptEntry> entries, Path session, long from, long to) { }

    /** A prompt that is only an attachment or only a slash command is not an instruction. */
    static boolean isInstruction(String text) {
        if (text == null) return false;
        var t = text.strip();
        if (t.isEmpty()) return false;
        if (t.startsWith("/")) return false;
        // "@prp/29-prp-name.md @prp/29-prp.status.md read 10% of …" - the opener, not an instruction.
        var withoutAttachments = t.replaceAll("@[A-Za-z0-9_./\\\\-]+", " ").strip();
        if (withoutAttachments.isEmpty()) return false;
        var words = withoutAttachments.split("\\s+").length;
        if (words <= 3) return false;
        return !isOpener(withoutAttachments.toLowerCase(Locale.ROOT));
    }

    static boolean isOpener(String lower) {
        return lower.startsWith("read last ") || lower.startsWith("read 10%")
            || lower.startsWith("read this prp") || lower.startsWith("read the prp");
    }

    /** Collected from one session file, oldest first, openers and slash commands dropped. */
    public static Found collect(Path session) {
        var all = TranscriptReader.read(session);
        var out = new ArrayList<TranscriptEntry>();
        var from = 0L;
        var to = 0L;
        for (var e : all) {
            if (e.timestamp() > 0) {
                if (from == 0 || e.timestamp() < from) from = e.timestamp();
                if (e.timestamp() > to) to = e.timestamp();
            }
            if (e.isUser() && isInstruction(e.body())) out.add(e);
        }
        return new Found(out, session, from, to);
    }

    /** Drops the ones whose text is already somewhere in the PRP, however the user pasted it. */
    public static List<TranscriptEntry> notAlreadyIn(List<TranscriptEntry> entries, String prpText) {
        var haystack = normalise(prpText);
        var out = new ArrayList<TranscriptEntry>();
        for (var e : entries) {
            var needle = normalise(e.body());
            if (needle.length() >= 24 && haystack.contains(needle)) continue;
            out.add(e);
        }
        return out;
    }

    /**
     * Whitespace and case flattened, so a line the user pasted with different wrapping still counts
     * as the same instruction. Nothing else is touched - punctuation carries meaning here.
     */
    static String normalise(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    /** The block that goes into the PRP. Delimited both ways so a regex can find either end. */
    public static String block(Found found, List<TranscriptEntry> keep, String sessionId) {
        // THE TAGS ARE THE DELIMITERS, and nothing else is. An earlier version wrapped the block in
        // `----` rules as well, and replacing a block then left the old rule behind and wrote a new
        // one - three lines of drift on every run. A boundary the replace cannot see is a boundary
        // that accumulates.
        var out = new ArrayList<String>();
        out.add("<user-instructions session=\"" + sessionId
            + "\" from=\"" + Stamp.of(found.from())
            + "\" to=\"" + Stamp.of(found.to())
            + "\" added=\"" + Stamp.now()
            + "\" count=\"" + keep.size() + "\">");
        out.add("");
        out.add("- Instructions given IN CONVERSATION during this cycle, collected by `proj prp instructions`.");
        out.add("- They are as binding as the requirement above. Anything already written into this PRP by hand is not repeated here.");
        out.add("");
        for (var e : keep) {
            out.add("- " + Marks.USER + " " + Stamp.of(e.timestamp()));
            for (var line : e.body().strip().split("\n", -1)) out.add(bullet(line));
            out.add("");
        }
        out.add("</user-instructions>");
        return String.join("\n", out);
    }

    /**
     * The PRP's own style is tab-indented bullets, so each line of an instruction becomes one - but
     * a line the user already wrote as a bullet must not get a second one. {@code - - the ui is nice}
     * is what happens without this, and it is exactly the sort of small ugliness that makes a
     * generated section look generated.
     */
    static String bullet(String line) {
        var t = line.strip();
        if (t.isEmpty()) return "";
        if (t.startsWith("- ")) return "\t" + t;
        if (t.startsWith("-\t")) return "\t- " + t.substring(2).strip();
        return "\t- " + t;
    }

    public static String sessionIdOf(Path session) {
        var name = session.getFileName().toString();
        var id = name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name;
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    /** The sessions of this project that opened on this PRP, oldest first. */
    public static List<Path> sessionsFor(Path projectDir, String prpIndex) {
        var out = new ArrayList<Path>();
        for (var f : SessionRead.sessionsOfDirectory(projectDir)) {
            if (prpIndex == null || prpIndex.equals(openedOn(f))) out.add(f);
        }
        out.sort((a, b) -> Long.compare(modified(a), modified(b)));
        return out;
    }

    /** The PRP index named in a session's FIRST typed prompt, or null if it opened on nothing. */
    public static String openedOn(Path session) {
        for (var e : TranscriptReader.read(session)) {
            if (!e.isUser()) continue;
            var named = PrpLink.mentioned(e.body());
            if (named == null) return null;
            var m = java.util.regex.Pattern.compile("(\\d{2})-prp[-.]").matcher(named);
            return m.find() ? m.group(1) : null;
        }
        return null;
    }

    static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }
}
