package littlejlib.proj.cli;

import littlejlib.proj.track.Budget;
import littlejlib.proj.track.Now;
import littlejlib.proj.track.PrpRefs;
import littlejlib.proj.track.Session;
import littlejlib.proj.track.Views;
import littlejlib.proj.track.Workplan;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "One work item in full; --history adds every earlier state")
public class PlanShowCmd implements Runnable {

    @Parameters(index = "0", description = "Item id")
    long id;

    @Option(names = "--history", description = "Every state the item went through, oldest first")
    boolean history;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var item = Workplan.current(prpDir).get(id);
            if (item == null) throw new IllegalArgumentException("no item " + id);
            var b = new Budget(PrpCommands.OUT, 0);
            Views.item(b, item, PrpRefs.of(prpDir));
            if (history) for (var h : Workplan.history(prpDir, id))
                b.line("  " + Now.shown(h.updated()) + "  " + h.type() + "  " + h.status() + "  " + h.title()
                        + "  (" + Session.shortId(h.session()) + ")");
        });
    }
}
