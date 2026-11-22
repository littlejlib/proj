package littlejlib.proj.cli;

import littlejlib.proj.track.Budget;
import littlejlib.proj.track.Jsonl;
import littlejlib.proj.track.PrpRefs;
import littlejlib.proj.track.Views;
import littlejlib.proj.track.Waiting;
import littlejlib.proj.track.Workplan;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Set;

/** The items as they stand now, waiting-for-the-user first. Done and dropped are hidden unless asked for. */
@Command(name = "list", aliases = "ls", description = "List work items as they stand now")
public class PlanListCmd implements Runnable {

    @Option(names = {"-t", "--type"}, description = "Only this type: todo, discuss, confirm")
    String type;

    @Option(names = {"-s", "--status"}, description = "Only this status (default: open, blocked, hold)")
    String status;

    @Option(names = {"-p", "--prp"}, description = "Only items owned by or related to this PRP (number or uuid)")
    String prp;

    @Option(names = "--all", description = "Include done and dropped")
    boolean all;

    @Option(names = "--budget", description = "Stop at about this many tokens and count what was left out")
    long budget;

    @Option(names = "--json", description = "The current state of each item as a json line")
    boolean json;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Option(names = "--everywhere", description = "Open items of every project proj knows (from the `proj prp find` cache), waiting-for-the-user first")
    boolean everywhere;

    @Option(names = "--for-me", description = "With --everywhere: only the items that wait for the user (open discuss and confirm)")
    boolean forMe;

    static final Set<String> LIVE = littlejlib.proj.track.Workplan.LIVE;

    @Override
    public void run() {
        TrackCli.run(() -> {
            if (everywhere) {
                var projects = Waiting.projects(PrpCommands.prpDir(dir).getParent());
                var b = new Budget(PrpCommands.OUT, budget);
                var lines = Waiting.everywhere(projects, forMe);
                b.line(lines.size() + " open items in " + projects.size() + " projects with a work plan");
                lines.forEach(b::line);
                b.end();
                return;
            }
            var prpDir = PrpCommands.prpDir(dir);
            var items = Workplan.current(prpDir);
            var owner = prp == null ? null : TrackCli.target(prpDir, prp);
            var shown = items.values().stream()
                    .filter(i -> type == null || type.equalsIgnoreCase(i.type()))
                    .filter(i -> status != null ? status.equalsIgnoreCase(i.status()) : all || LIVE.contains(i.status()))
                    .filter(i -> owner == null || owner.equals(i.prp()) || (i.related() != null && i.related().contains(owner)))
                    .toList();
            if (json) {
                shown.forEach(i -> PrpCommands.OUT.println(Jsonl.GSON.toJson(i)));
                return;
            }
            var b = new Budget(PrpCommands.OUT, budget);
            Views.items(b, items, shown, PrpRefs.of(prpDir));
            b.end();
        });
    }
}
