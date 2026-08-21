package littlejlib.proj.cct.cli;

import module java.base;

import picocli.CommandLine.*;
import littlejlib.proj.cct.action.*;
import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.search.SearchService;
import littlejlib.proj.cct.store.*;

import static littlejlib.proj.cct.Num.*;

@Command(name = "show", description = "Print a session as its turns, human turns first-class.")
public final class ShowCmd implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "SESSION", description = "Session id or any unambiguous prefix.")
    String session;

    @Option(names = {"-l", "--layer"}, description = "1 human only (default), 2 plus Claude's text, 3 plus thinking.")
    int layer = 1;

    @Option(names = {"-w", "--width"}, description = "Wrap turns to this many characters, 0 for no truncation.")
    int width = 0;

    @Override
    public Integer call() {
        var db = Db.get();
        try {
            var found = Sessions.byPrefix(db.db(), session);
            if (found.isEmpty()) { Out.line("no session matches " + session); return 1; }
            if (found.size() > 1) { ambiguous(found); return 2; }
            print(new SearchService(db.db()), found.get(0));
            return 0;
        } finally {
            db.shutdown();
        }
    }

    private void ambiguous(List<littlejlib.proj.cct.model.SessionDoc> found) {
        Out.line("%d sessions start with that prefix:", found.size());
        for (var s : found) Out.line("  %s  %s", s.sessionId(), Out.oneLine(s.aiTitle(), 60));
    }

    private void print(SearchService svc, littlejlib.proj.cct.model.SessionDoc s) {
        Out.line("%s", s.aiTitle() == null ? "(untitled session)" : s.aiTitle());
        Out.line("%s", s.cwd() == null ? "(no cwd recorded)" : s.cwd());
        Out.line("%s  ·  %s  ·  %s records  ·  %s  ·  %d human turns",
            s.sessionId(), Timestamps.local(n(s.lastActivity())),
            count(n(s.recordCount())), bytes(n(s.fileBytes())), n(s.humanTurns()));
        if (n(s.imageLineBytes()) > 0)
            Out.line("images: %s of %s is inline image lines",
                bytes(n(s.imageLineBytes())), bytes(n(s.fileBytes())));
        Out.blank();
        for (var t : svc.turns(s.sessionId(), layer)) {
            var tag = Layer.ofDepth(n(t.layer())).label;
            Out.line("[%s] %s%s", tag, Timestamps.local(n(t.timestamp())),
                n(t.sidechain()) ? "  (sidechain)" : "");
            Out.line("%s", width > 0 ? Out.oneLine(t.text(), width) : t.text());
            Out.blank();
        }
        var note = ResumeCommands.note(s);
        if (!note.isBlank()) Out.line("note: %s", note);
        Out.line("%s", ResumeCommands.forThisPlatform(s));
    }
}
