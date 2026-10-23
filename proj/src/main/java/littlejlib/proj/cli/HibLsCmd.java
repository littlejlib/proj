package littlejlib.proj.cli;

import com.google.gson.GsonBuilder;
import littlejlib.proj.hib.LiveSession;
import littlejlib.proj.hib.LiveSessions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.LinkedHashMap;

import static littlejlib.proj.cli.PrpCommands.OUT;
import static littlejlib.proj.hib.ProcMem.mb;

@Command(name = "ls", aliases = "list", mixinStandardHelpOptions = true,
        description = "Running claude-code conversations: idle time, RAM (working set), committed (private), PRP")
public final class HibLsCmd implements Runnable {

    @Option(names = "--full", description = "Also read each transcript for its turn count and PRP (slower)")
    boolean full;

    @Option(names = "--json", description = "One JSON array, for scripts and agents (implies --full)")
    boolean json;

    @Override
    public void run() {
        var now = System.currentTimeMillis();
        var all = LiveSessions.all();
        if (full || json) LiveSessions.withDetail(all);
        var rows = all.stream().map(s -> row(s, now, full || json)).toList();
        if (json) {
            OUT.println(new GsonBuilder().setPrettyPrinting().create().toJson(rows));
            return;
        }
        OUT.printf("%-7s %-34s %-8s %6s %6s %7s %5s  %-8s %s%n",
                "pid", "name", "status", "idle", "RAM", "commit", "turns", "prp", "cwd");
        for (var r : rows)
            OUT.printf("%-7s %-34s %-8s %6s %6s %7s %5s  %-8s %s%n", r.get("pid"), cut(r.get("name"), 34),
                    r.get("status"), r.get("idle"), r.get("ramMb"), r.get("committedMb"),
                    r.getOrDefault("turns", ""), r.getOrDefault("prp", ""), r.get("cwd"));
        var ram = all.stream().mapToLong(s -> s.workingSet() == null ? 0 : s.workingSet()).sum();
        var commit = all.stream().mapToLong(s -> s.privateBytes() == null ? 0 : s.privateBytes()).sum();
        OUT.println(all.size() + " conversations, " + mb(ram) + " MB RAM, " + mb(commit) + " MB committed"
                + "  (idle in minutes; hibernate with  proj hib tab <name>)");
    }

    static LinkedHashMap<String, Object> row(LiveSession s, long now, boolean full) {
        var m = new LinkedHashMap<String, Object>();
        m.put("pid", s.pid());
        m.put("name", s.label());
        m.put("status", s.status());
        m.put("idle", idle(s.idleMinutes(now)));
        m.put("idleMinutes", s.idleMinutes(now));
        m.put("ramMb", mb(s.workingSet()));
        m.put("committedMb", mb(s.privateBytes()));
        m.put("cwd", s.cwd());
        m.put("sessionId", s.sessionId());
        m.put("closesTab", s.shellPid() != null);
        if (full) {
            m.put("turns", s.humanTurns() == null ? "?" : s.humanTurns().toString());
            m.put("prp", s.prpIndex() == null ? "?" : s.prpIndex());
        }
        return m;
    }

    static String idle(long minutes) {
        if (minutes < 0) return "?";
        if (minutes < 60) return minutes + "m";
        if (minutes < 48 * 60) return (minutes / 60) + "h";
        return (minutes / 1440) + "d";
    }

    static String cut(Object o, int n) {
        var s = String.valueOf(o);
        return s.length() <= n ? s : s.substring(0, n - 1) + "~";
    }
}
