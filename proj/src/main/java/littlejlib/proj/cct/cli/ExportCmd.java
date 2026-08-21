package littlejlib.proj.cct.cli;

import module java.base;

import picocli.CommandLine.*;
import littlejlib.proj.cct.action.Sessions;
import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.export.*;
import littlejlib.proj.cct.search.SearchService;

@Command(name = "export", description = "Write a session out as markdown, text, semantic xhtml or raw jsonl.")
public final class ExportCmd implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "SESSION", description = "Session id or any unambiguous prefix.")
    String session;

    @Option(names = {"-f", "--format"}, description = "md (default), txt, xhtml, jsonl.")
    String format = "md";

    @Option(names = {"-l", "--layer"}, description = "1 human turns (default), 2 plus Claude's text, 3 plus thinking.")
    int layer = 2;

    @Option(names = {"-o", "--out"}, description = "Output file or directory. Default is the exports folder.")
    String out;

    @Option(names = "--stdout", description = "Write to stdout instead of a file.")
    boolean toStdout;

    @Override
    public Integer call() throws IOException {
        var fmt = ExportFormat.of(format);
        var db = Db.get();
        try {
            var found = Sessions.byPrefix(db.db(), session);
            if (found.isEmpty()) { Out.note("no session matches " + session); return 1; }
            if (found.size() > 1) {
                Out.note("%d sessions start with that prefix; be more specific", found.size());
                for (var s : found) Out.note("  %s  %s", s.sessionId(), Out.oneLine(s.aiTitle(), 60));
                return 2;
            }
            var s = found.get(0);
            var turns = new SearchService(db.db()).turns(s.sessionId(), layer);
            if (toStdout) {
                if (fmt == ExportFormat.JSONL) { Out.note("jsonl is a file copy; drop --stdout"); return 2; }
                Out.line(TranscriptExport.render(s, turns, fmt));
                return 0;
            }
            var written = TranscriptExport.write(s, turns, fmt, target(s, fmt));
            Out.line(written.toString());
            return 0;
        } finally {
            db.shutdown();
        }
    }

    private Path target(littlejlib.proj.cct.model.SessionDoc s, ExportFormat fmt) throws IOException {
        if (out == null || out.isBlank()) {
            littlejlib.proj.cct.store.StorePaths.ensureAppHome();
            return ExportNames.resolve(littlejlib.proj.cct.store.StorePaths.exportDir(), s, fmt);
        }
        var p = Path.of(out);
        return Files.isDirectory(p) ? ExportNames.resolve(p, s, fmt) : p;
    }
}
