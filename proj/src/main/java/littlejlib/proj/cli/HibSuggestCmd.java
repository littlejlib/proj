package littlejlib.proj.cli;

import com.google.gson.GsonBuilder;
import littlejlib.proj.hib.*;
import littlejlib.proj.ui.HibApprovalWindow;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.LinkedHashMap;
import java.util.List;

import static littlejlib.proj.cli.PrpCommands.ERR;
import static littlejlib.proj.cli.PrpCommands.OUT;
import static littlejlib.proj.hib.ProcMem.mb;

@Command(name = "suggest", mixinStandardHelpOptions = true,
        description = "Which idle conversations to hibernate, longest idle first. A dry run unless --apply, "
                + "which asks the human in a window (F2 approves) and hibernates what they tick")
public final class HibSuggestCmd implements Runnable {

    @Option(names = "--min-idle", description = "Minutes idle before a conversation is suggested (default ${DEFAULT-VALUE})")
    long minIdle = 120;

    @Option(names = "--apply", description = "Ask for approval in a window, then hibernate the approved ones")
    boolean apply;

    @Option(names = "--json", description = "The candidates as JSON, for agents (dry run only)")
    boolean json;

    @Override
    public void run() {
        var self = LiveSessions.self();
        var all = LiveSessions.withDetail(LiveSessions.all());
        var cs = Suggest.of(all, self == null ? null : self.pid(), minIdle);
        if (json) { OUT.println(new GsonBuilder().setPrettyPrinting().create().toJson(cs.stream().map(HibSuggestCmd::row).toList())); return; }
        if (cs.isEmpty()) { OUT.println("nothing idle for " + Suggest.duration(minIdle) + " or more, of " + all.size() + " running"); return; }
        for (var c : cs)
            OUT.println((c.onPrp() ? "  " : "* ") + c.session().label() + "  " + c.reason()
                    + (c.onPrp() ? "  PRP " + c.session().prpIndex() : ""));
        OUT.println(cs.size() + " of " + all.size() + " running, together " + mb(Suggest.ram(cs)) + " MB RAM, "
                + mb(Suggest.committed(cs)) + " MB committed.  * = not on a PRP (needs ticking, logs to "
                + HibStore.looseLogs() + ")");
        if (!apply) { OUT.println("dry run - add --apply to choose in a window"); return; }
        approveAndRun(cs, self);
    }

    void approveAndRun(List<Suggest.Candidate> cs, LiveSession self) {
        var asked = self != null ? "the conversation " + self.label() + " (" + self.cwd() + ")"
                : "a terminal in " + System.getProperty("user.dir");
        var ids = HibApprovalWindow.ask(cs, asked);
        if (ids == null) { ERR.println("no approval window could be shown - nothing hibernated"); System.exit(3); return; }
        if (ids.isEmpty()) { OUT.println("cancelled - nothing hibernated"); return; }
        var worst = 0;
        for (var c : cs) {
            if (!ids.contains(c.session().sessionId())) continue;
            var now = LiveSessions.find(String.valueOf(c.session().pid())).stream().findFirst().orElse(null);
            if (now == null) { OUT.println(c.session().label() + ": already gone"); continue; }
            if (!"idle".equalsIgnoreCase(now.status())) { OUT.println(now.label() + ": became " + now.status() + " - skipped"); continue; }
            worst = Math.max(worst, HibCli.report(Hibernator.run(now, false, !c.onPrp(), false, false)));
        }
        System.exit(worst);
    }

    static LinkedHashMap<String, Object> row(Suggest.Candidate c) {
        var m = new LinkedHashMap<String, Object>();
        var s = c.session();
        m.put("pid", s.pid());
        m.put("name", s.label());
        m.put("idleMinutes", s.idleMinutes(System.currentTimeMillis()));
        m.put("turns", s.humanTurns());
        m.put("ramMb", mb(s.workingSet()));
        m.put("committedMb", mb(s.privateBytes()));
        m.put("prp", c.onPrp() ? s.prpIndex() : null);
        m.put("cwd", s.cwd());
        m.put("reason", c.reason());
        return m;
    }
}
