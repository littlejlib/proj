package littlejlib.proj.cct.export;

import module java.base;

import littlejlib.proj.cct.action.ResumeCommands;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static luvml.E.*;
import static luvml.A.*;
import static luvml.Frags.*;
import static luvml.ProcessingInstruction.xmlDeclaration;
import static littlejlib.proj.cct.export.TranscriptDsl.*;
import static littlejlib.proj.cct.Num.*;

import luvml.DocType;
import luvml.o.XHtmlStringRenderer;

public final class XHtmlExport {

    private XHtmlExport() {}

    static final String
        PUBROOT = "https://xyz-jphil.github.io/ccapis-static/",
        CSS = PUBROOT + "styles.css",
        JS = PUBROOT + "scripts.js",
        TYPE = "claude-code Session Semantic XHTML5",
        DEFINITION = "Browser-renderable transcript of one claude-code session, in the CCAPI "
            + "Conversation format. Structure: conversation > metadata + messages > (human|assistant) "
            + "elements. Content is raw markdown text preserved as-is for AI parsing.",
        TRIVIA = "This XHTML reads as plain markdown with no HTML parsing needed; the tags exist only "
            + "so a browser can render it. Source is the local claude-code jsonl store, not the web api.";

    public static String render(SessionDoc s, List<TurnDoc> turns) {
        var title = MarkdownExport.title(s);
        var doc = frags(
            xmlDeclaration("UTF-8"),
            DocType.html5(),
            html(xmlns("http://www.w3.org/1999/xhtml"),
                head(
                    meta(charset("UTF-8")),
                    meta(name("viewport"), content("width=device-width, initial-scale=1.0")),
                    meta(name("description"), content(DEFINITION)),
                    meta(name("trivia"), content(TRIVIA)),
                    luvml.E.title(title + " (" + TYPE + ")"),
                    link(rel("stylesheet"), href(CSS)),
                    script(src(JS))
                ),
                body(
                    luvml.E.style("human, assistant, metadata, messages { display: block; }"),
                    h1(text(title)),
                    metadataOf(s),
                    messagesOf(turns)
                )
            )
        );
        return XHtmlStringRenderer.asFormatted(doc, "");
    }

    static TranscriptDsl.Metadata_E metadataOf(SessionDoc s) {
        var rows = frags();
        rows.____(h2(text("Session metadata")));
        rows.____(div(text("Session id: " + s.sessionId())));
        rows.____(div(text("Folder: " + (s.cwd() == null || s.cwd().isBlank() ? "(none recorded)" : s.cwd()))));
        rows.____(div(text("Last active: " + Timestamps.local(n(s.lastActivity())))));
        rows.____(div(text(n(s.transcriptMissing())
            ? "Prompts only: claude-code has pruned this transcript"
            : count(n(s.recordCount())) + " records, " + n(s.humanTurns()) + " of your turns")));
        if (s.models() != null && !s.models().isBlank()) rows.____(div(text("Models: " + s.models())));
        if (s.gitBranch() != null && !s.gitBranch().isBlank()) rows.____(div(text("Branch: " + s.gitBranch())));
        var note = ResumeCommands.note(s);
        if (!note.isBlank()) rows.____(div(text("Note: " + note)));
        rows.____(div(text("Resume: " + ResumeCommands.forThisPlatform(s))));
        return metadata(rows);
    }

    static TranscriptDsl.Messages_E messagesOf(List<TurnDoc> turns) {
        var body = frags();
        for (var i = 0; i < turns.size(); i++) {
            var t = turns.get(i);
            var layer = Layer.ofDepth(n(t.layer()));
            var at = Timestamps.local(n(t.timestamp()));
            var content = t.text() == null ? "" : t.text();
            if (layer == Layer.HUMAN)
                body.____(human(serialNum(i + 1), uuid(nz(t.uuid())), created_at(at), text(content)));
            else
                body.____(assistant(serialNum(i + 1), uuid(nz(t.uuid())), created_at(at),
                    speaker(layer.label), text(content)));
        }
        return messages(body);
    }

    static String nz(String s) { return s == null ? "" : s; }
}
