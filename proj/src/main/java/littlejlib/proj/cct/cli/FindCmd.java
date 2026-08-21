package littlejlib.proj.cct.cli;

import module java.base;

import picocli.CommandLine.*;
import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.search.*;

@Command(name = "find", description = "Search every claude-code session on this machine by content.")
public final class FindCmd implements Callable<Integer> {

    @Parameters(arity = "0..*", paramLabel = "TEXT", description = "What to look for. Empty lists the most recent sessions.")
    List<String> words = new ArrayList<>();

    @Option(names = {"-l", "--layer"}, description = "1 human turns (default), 2 plus Claude's text, 3 plus thinking.")
    int layer = 1;

    @Option(names = {"-m", "--mode"}, description = "auto, substring, phrase, keywords.")
    String mode = "auto";

    @Option(names = {"-p", "--project"}, description = "Restrict to one project folder key.")
    String project;

    @Option(names = "--cwd", description = "Restrict to sessions whose recorded directory contains this text.")
    String cwdContains;

    @Option(names = "--model", description = "Restrict to sessions that used this model.")
    String model;

    @Option(names = "--branch", description = "Restrict to a git branch.")
    String branch;

    @Option(names = {"-n", "--limit"}, description = "Maximum sessions to show (default 20).")
    int limit = 20;

    @Option(names = "--no-resume", description = "Omit the ready-to-paste resume command.")
    boolean noResume;

    @Override
    public Integer call() {
        var q = new SearchQuery()
            .text(String.join(" ", words))
            .layer(layer)
            .mode(mode)
            .projectEncoded(project)
            .cwdContains(cwdContains)
            .model(model)
            .gitBranch(branch)
            .limit(limit);
        var db = Db.get();
        try {
            var hits = new SearchService(db.db()).find(q);
            HitRenderer.render(hits, !noResume);
            return hits.isEmpty() ? 1 : 0;
        } finally {
            db.shutdown();
        }
    }
}
