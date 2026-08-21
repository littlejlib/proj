package littlejlib.proj.cct.export;

import module java.base;

import littlejlib.proj.cct.action.ResumeCommands;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static littlejlib.proj.cct.Num.*;

/**
 * A conversation as markdown, and the house paragraph rule is the whole reason this is not a
 * one-liner: NOTHING here wraps. A paragraph is one line however long it is, separated from the
 * next by a blank line, because these exports get pasted into whatsapp and into documents, where a
 * hard newline mid-sentence survives and ruins the wrap the reader's own client would have done.
 */
public final class MarkdownExport {

    private MarkdownExport() {}

    public static String render(SessionDoc s, List<TurnDoc> turns) {
        var out = new ArrayList<String>();
        out.add("# " + title(s));
        out.add("");
        out.addAll(facts(s));
        out.add("");
        out.add("---");
        out.add("");
        for (var t : turns) {
            out.add("## " + speaker(t) + "   ·   " + Timestamps.local(n(t.timestamp()))
                + (n(t.sidechain()) ? "   ·   sub-agent" : ""));
            out.add("");
            out.add(body(t));
            out.add("");
        }
        return String.join("\n", out);
    }

    static String title(SessionDoc s) {
        return s.aiTitle() == null || s.aiTitle().isBlank() ? "(untitled session)" : s.aiTitle();
    }

    static String speaker(TurnDoc t) {
        var layer = Layer.ofDepth(n(t.layer()));
        return layer == Layer.HUMAN ? "You" : layer == Layer.ASSISTANT ? "Claude" : layer.label;
    }

    static String body(TurnDoc t) {
        return t.text() == null ? "" : t.text().strip();
    }

    static List<String> facts(SessionDoc s) {
        var out = new ArrayList<String>();
        out.add("- folder: `" + (s.cwd() == null || s.cwd().isBlank() ? "(none recorded)" : s.cwd()) + "`");
        out.add("- session: `" + s.sessionId() + "`");
        out.add("- last active: " + Timestamps.local(n(s.lastActivity())));
        if (n(s.transcriptMissing())) out.add("- prompts only: claude-code has pruned this transcript");
        else out.add("- " + count(n(s.recordCount())) + " records, " + n(s.humanTurns()) + " of your turns");
        if (s.models() != null && !s.models().isBlank()) out.add("- models: " + s.models());
        if (s.gitBranch() != null && !s.gitBranch().isBlank()) out.add("- branch: " + s.gitBranch());
        var note = ResumeCommands.note(s);
        if (!note.isBlank()) out.add("- note: " + note);
        out.add("- resume: `" + ResumeCommands.forThisPlatform(s) + "`");
        return out;
    }
}
