package littlejlib.proj.cli;

import littlejlib.proj.track.Budget;
import littlejlib.proj.track.Conversation;
import littlejlib.proj.track.MetaFile;
import littlejlib.proj.track.Now;
import littlejlib.proj.track.PrpRefs;
import littlejlib.proj.track.Recall;
import littlejlib.proj.track.RecallSource;
import littlejlib.proj.track.Session;
import littlejlib.proj.track.Timeline;
import littlejlib.proj.track.Views;
import littlejlib.proj.track.Waiting;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * What a new session needs from the previous one, within a token budget: where the PRP stands, then
 * the previous session's conversation (the human's words and Claude's text replies, no tool calls,
 * no tool output, no code). Replaces "read the last 10 percent of the log".
 */
@Command(name = "recall", description = "Print the PRP's state and the previous session's conversation within a token budget")
public class PrpRecallCmd implements Runnable {

    @Parameters(arity = "0..1", description = "PRP index. Defaults to the PRP this session opened on")
    String index;

    @Option(names = "--budget", description = "Tokens to spend (characters / 4). Default " + Recall.DEFAULT_BUDGET)
    long budget = Recall.DEFAULT_BUDGET;

    @Option(names = "--session", description = "Recall this session (id or prefix) instead of the newest earlier one")
    String session;

    @Option(names = "--list", description = "List the sessions known for this PRP, newest first, and stop")
    boolean list;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var entry = PrpCommands.resolve(prpDir, index);
            var all = RecallSource.of(prpDir, entry);
            var current = Session.current(null);
            if (list) {
                all.forEach(s -> PrpCommands.OUT.println(s.sessionId() + "  " + Now.shown(s.modified())
                        + "  " + s.file() + (s.sessionId().equals(current) ? "  (this session)" : "")));
                return;
            }
            var statusShare = Math.max(500, budget / 10);
            var b = new Budget(PrpCommands.OUT, statusShare);
            Views.timeline(b, entry, TrackCli.uuid(entry), MetaFile.read(prpDir, entry.getIndex()),
                    Timeline.read(prpDir, entry.getIndex()), false, PrpRefs.of(prpDir));
            b.end();
            var open = Waiting.open(prpDir, TrackCli.uuid(entry));
            if (!open.isEmpty()) {
                var ib = new Budget(PrpCommands.OUT, statusShare);
                var forUser = open.stream().filter(Waiting::forUser).count();
                ib.line("open work items of this PRP: " + open.size() + (forUser > 0 ? ", " + forUser + " waiting for the user (ask them)" : ""));
                for (var i : open) ib.line("  #" + i.id() + "  " + i.type() + "  " + i.status() + "  " + Budget.cut(i.title(), Views.TITLE_SHOWN));
                ib.end();
            }
            var source = RecallSource.pick(all, current, session);
            if (source == null) {
                PrpCommands.OUT.println("(no earlier session of this PRP found" + (all.isEmpty() ? "" : "; only this one") + ")");
                return;
            }
            var c = Conversation.read(source.file());
            PrpCommands.OUT.println();
            PrpCommands.OUT.println("# previous session " + source.sessionId() + "  " + Now.shown(c.first()) + " to " + Now.shown(c.last())
                    + "  (" + c.turns().size() + " turns; tool calls and code left out)");
            Recall.render(c, Math.max(1000, budget - statusShare)).forEach(PrpCommands.OUT::println);
        });
    }
}
