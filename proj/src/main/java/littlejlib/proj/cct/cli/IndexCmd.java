package littlejlib.proj.cct.cli;

import module java.base;

import picocli.CommandLine.*;
import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.index.*;

@Command(name = "index", description = "Scan the claude-code store and update the local index.")
public final class IndexCmd implements Callable<Integer> {

    @Option(names = {"-f", "--force"}, description = "Rebuild every session instead of tailing unchanged files.")
    boolean force;

    @Option(names = "--no-assistant", description = "Skip Claude's own message text (layer 2).")
    boolean noAssistant;

    @Option(names = "--no-thinking", description = "Skip thinking blocks (layer 3).")
    boolean noThinking;

    @Option(names = "--quiet", description = "Suppress per-file progress.")
    boolean quiet;

    @Override
    public Integer call() {
        var opts = IndexOptions.defaults()
            .force(force)
            .includeAssistant(!noAssistant)
            .includeThinking(!noThinking);
        var db = Db.get();
        try {
            var report = Indexer.run(db.db(), opts, quiet ? s -> { } : Out::note);
            Out.line(report.summary());
            return report.failed() > 0 ? 1 : 0;
        } finally {
            db.shutdown();
        }
    }
}
