package littlejlib.proj.cct.export;

import module java.base;

import littlejlib.proj.cct.action.ResumeCommands;
import littlejlib.proj.cct.model.SessionDoc;

import static littlejlib.proj.cct.Num.*;

/**
 * A session as a markdown log: {@code ## ❯ opening words} for a turn of yours,
 * {@code ## ● opening words} for one of Claude's, and the tool calls and their output inline under it
 * in the shape {@code /export} uses.
 * <p>
 * WHY MARKDOWN AND NOT THE .txt THAT /export WRITES. Three things fall out of it and all three were
 * the point. VSCode's outline becomes the index, so navigating a 5 MB log is a click rather than a
 * scroll. {@code ^## ❯} greps every human turn and nothing else - not a {@code ##} that happens to
 * appear inside a transcript, because ours always carries a speaker mark. And the marks are
 * claude-code's own, so one pattern searches these files and any {@code /export} output beside them.
 * <p>
 * NOTHING IS WRAPPED. {@code /export} hard-wraps at about 78 columns; that is right for a terminal
 * and wrong for a file that gets pasted into documents and chat, so a paragraph here is one line
 * however long it is - the house rule, and the reason these exports survive being copied.
 * <p>
 * There is no line-number index. With {@code ##} headings the outline is the index and a grep gives
 * the same answer, so computing one would be a second thing to keep exactly right for no gain.
 */
public final class SessionLogMd {

    private SessionLogMd() {}

    public static String render(SessionDoc s, List<TranscriptEntry> entries, LogHeading h, int resultLines) {
        var out = new ArrayList<String>();
        out.addAll(header(s, h, entries));
        for (var e : entries) section(out, e, resultLines);
        out.add("");
        return String.join("\n", out);
    }

    static void section(List<String> out, TranscriptEntry e, int resultLines) {
        switch (e.kind()) {
            case TranscriptEntry.USER, TranscriptEntry.QUEUED -> turn(out, e, Marks.USER,
                TranscriptEntry.QUEUED.equals(e.kind()) ? " (queued)" : "");
            case TranscriptEntry.ASSISTANT -> turn(out, e, Marks.AGENT, agentMeta(e));
            case TranscriptEntry.THINKING -> {
                out.add("");
                out.add(Marks.THINKING + " thinking");
                out.add("");
                out.add(e.body());
            }
            case TranscriptEntry.TOOL_USE -> tool(out, e);
            case TranscriptEntry.TOOL_RESULT -> result(out, e, resultLines);
            default -> { }
        }
    }

    static void turn(List<String> out, TranscriptEntry e, String mark, String suffix) {
        out.add("");
        out.add("## " + mark + " " + opening(e.body()));
        out.add("");
        out.add("`" + Stamp.of(e.timestamp()) + "`" + suffix);
        out.add("");
        out.add(e.body().strip());
    }

    static void tool(List<String> out, TranscriptEntry e) {
        var brief = ToolInput.brief(e.tool(), e.body());
        out.add("");
        out.add(Marks.AGENT + " " + brief);
        var body = e.body() == null ? "" : e.body().strip();
        if (body.isEmpty()) return;
        var lines = body.split("\n", -1);
        // The call line already shows the leading argument, so printing it again immediately below is
        // noise on every Write and every Edit. Skipped only when the brief shows it WHOLE - a long
        // shell command is truncated up there and must survive down here.
        var from = lines.length > 1 && brief.contains(lines[0]) ? 1 : 0;
        if (from >= lines.length) return;
        out.add("```");
        for (var i = from; i < lines.length; i++) out.add(lines[i]);
        out.add("```");
    }

    static void result(List<String> out, TranscriptEntry e, int max) {
        var body = e.body() == null ? "" : e.body().strip();
        if (body.isEmpty()) return;
        var lines = body.split("\n", -1);
        out.add("");
        out.add("  " + Marks.RESULT + (e.error() ? "  ERROR" : ""));
        out.add("```");
        var shown = max <= 0 ? lines.length : Math.min(max, lines.length);
        for (var i = 0; i < shown; i++) out.add(lines[i]);
        // Said, not silent. A log that quietly drops output is a log that cannot be trusted about
        // what it does show, so the count of what was left out is part of the record.
        if (shown < lines.length)
            out.add(Marks.ELIDED + " +" + (lines.length - shown) + " lines (--full to keep them)");
        out.add("```");
    }

    /**
     * The heading: the first sentence, or the first words up to a cap. A heading is what the outline
     * shows and what a grep prints, so it has to be the part that identifies the turn - which is
     * nearly always its opening clause.
     */
    static String opening(String body) {
        if (body == null || body.isBlank()) return "(empty)";
        var flat = body.replaceAll("\\s+", " ").strip();
        var end = sentenceEnd(flat);
        var cut = end > 0 && end <= 90 ? flat.substring(0, end) : flat;
        if (cut.length() > 90) cut = cut.substring(0, 89) + Marks.ELIDED;
        // A newline can never reach a heading, and a heading that starts with # would nest wrongly.
        return cut.replace('#', ' ').strip();
    }

    static int sentenceEnd(String s) {
        var best = -1;
        for (var i = 0; i < s.length() && i < 120; i++) {
            var c = s.charAt(i);
            if (c == '.' || c == '?' || c == '!' || c == ';' || c == ':') {
                // Not a decimal point, a version number or an ellipsis.
                if (i + 1 < s.length() && !Character.isWhitespace(s.charAt(i + 1))) continue;
                best = i;
                break;
            }
        }
        return best;
    }

    static String agentMeta(TranscriptEntry e) {
        var parts = new ArrayList<String>();
        if (e.model() != null) parts.add(e.model());
        if (e.effort() != null) parts.add("effort " + e.effort());
        if (e.sidechain()) parts.add("sub-agent" + (e.agentId() == null ? "" : " " + e.agentId()));
        return parts.isEmpty() ? "" : "  ·  " + String.join("  ·  ", parts);
    }

    static List<String> header(SessionDoc s, LogHeading h, List<TranscriptEntry> entries) {
        var users = entries.stream().filter(TranscriptEntry::isUser).count();
        var agents = entries.stream().filter(TranscriptEntry::isAssistant).count();
        var tools = entries.stream().filter(e -> TranscriptEntry.TOOL_USE.equals(e.kind())).count();
        var out = new ArrayList<String>();
        out.add("# " + h.title());
        out.add("");
        out.add(h.serial() == null ? "claude-code session log" : "claude-code session log " + h.serial());
        out.add("");
        for (var line : h.lines()) if (!line.isBlank()) out.add("- " + line);
        out.add("- session   `" + s.sessionId() + "`");
        out.add("- folder    `" + (s.cwd() == null ? "(none recorded)" : s.cwd()) + "`");
        out.add("- ran       " + Stamp.of(n(s.startedAt())) + "  to  " + Stamp.of(n(s.lastActivity())) + span(s));
        out.add("- exported  " + Stamp.now());
        out.add("- size      " + count(n(s.recordCount())) + " records, " + bytes(n(s.fileBytes()))
            + (n(s.imageLineBytes()) > 0 ? ", of which " + bytes(n(s.imageLineBytes())) + " is images" : ""));
        out.add("- entries   " + users + " yours, " + agents + " from Claude, " + tools + " tool calls");
        if (s.models() != null) out.add("- models    " + s.models());
        if (s.efforts() != null) out.add("- effort    " + s.efforts());
        if (s.gitBranch() != null) out.add("- branch    " + s.gitBranch());
        if (n(s.compactions()) > 0) out.add("- compacted " + n(s.compactions()) + " times");
        out.add("- resume    `" + ResumeCommands.forThisPlatform(s) + "`");
        out.add("");
        out.add("Grep `^## " + Marks.USER + "` for your turns, `^## " + Marks.AGENT
            + "` for Claude's, `^" + Marks.AGENT + " ` for tool calls.");
        return out;
    }

    static String span(SessionDoc s) {
        var ms = n(s.lastActivity()) - n(s.startedAt());
        if (ms <= 0) return "";
        var mins = ms / 60_000;
        return mins < 60 ? "  (" + mins + "m)" : "  (" + (mins / 60) + "h " + (mins % 60) + "m)";
    }
}
