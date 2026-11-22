package littlejlib.proj.cli;

import littlejlib.proj.track.PrpRefs;
import littlejlib.proj.track.Workplan;
import littlejlib.proj.track.WorkplanCheck;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Flags broken deps, cycles, items of closed PRPs and stale items. Exit 1 when something is flagged. */
@Command(name = "check", description = "Flag broken or obsolete work items; changes nothing")
public class PlanCheckCmd implements Runnable {

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var items = Workplan.current(prpDir);
            var problems = new java.util.ArrayList<>(WorkplanCheck.problems(items, PrpRefs.of(prpDir)));
            // Across projects: links into other plans (missing items, cycles), claims, shared resources.
            var report = littlejlib.proj.report.ReportBuilder.build(littlejlib.proj.report.ReportQuery.of(java.util.Map.of("done", "1")),
                    prpDir.getParent(), littlejlib.proj.track.WorkGraph::readPrps);
            for (var p : report.problems)
                if (p.startsWith("dependency cycle") ? p.contains(" -> ") && !problems.contains(p) && crossesProjects(p)
                        : !p.contains(": depends on ") && !p.contains(" is not in ") && !p.contains("but its PRP is closed") && !p.contains("which was dropped"))
                    problems.add(p);
            problems.forEach(PrpCommands.OUT::println);
            PrpCommands.OUT.println(items.size() + " items, " + problems.size() + " flagged");
            if (!problems.isEmpty()) System.exit(1);
        });
    }

    /** "a#1 -> b#2 -> a#1": a cycle the per-project check cannot see. */
    static boolean crossesProjects(String cycle) {
        return java.util.Arrays.stream(cycle.replace("dependency cycle: ", "").split(" -> ")).map(k -> k.substring(0, k.lastIndexOf('#'))).distinct().count() > 1;
    }
}
