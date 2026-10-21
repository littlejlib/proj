package littlejlib.proj.cli;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.group.Groups;
import littlejlib.proj.workspace.WorkspaceRecord;
import littlejlib.proj.workspace.WorkspaceStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static littlejlib.proj.cct.cli.Out.line;
import static littlejlib.proj.cct.cli.Out.note;

/**
 * The folders of the plain groups, newest activity first - the command-line form of what the tab
 * shows. With no group it lists the groups themselves, which is the fastest way to find out whether
 * a settings line took effect.
 */
@Command(name = "list", description = "Folders carrying PRPs, newest activity first")
public final class WsListCmd implements Runnable {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Parameters(index = "0", arity = "0..1", description = "Group slug; lists the groups when omitted")
    String group;

    @Option(names = "--limit", description = "How many rows (default 40)")
    int limit = 40;

    @Option(names = "--all", description = "Ancestor nodes too, not only folders that hold PRPs")
    boolean all;

    @Option(names = "--json", description = "JSON instead of plain text")
    boolean json;

    @Override
    public void run() {
        var settings = new SettingsManager().loadSettings();
        if (group == null || group.isBlank()) {
            listGroups(settings);
            return;
        }
        var g = Groups.bySlug(settings, group);
        if (g == null || g.isCode()) {
            note("no plain group called '" + group + "'");
            return;
        }
        var rows = new WorkspaceStore().loadGroup(g.slug()).stream()
                .filter(r -> all || r.hasPrp())
                .sorted(java.util.Comparator.comparingLong(WorkspaceRecord::activity).reversed())
                .toList();

        if (rows.isEmpty()) {
            note("nothing indexed for '" + g.slug() + "' - run 'proj ws scan " + g.slug() + "'");
            return;
        }
        if (json) {
            line(QueryCommands.GSON.toJson(rows.stream().limit(limit).map(WsListCmd::asMap).toList()));
            return;
        }
        rows.stream().limit(limit).forEach(r -> line("  %s  %-5s  %s".formatted(
                day(r.activity()), r.hasPrp() ? r.prpCount() + " prp" : "", r.relativePath())));
        if (rows.size() > limit) line("  ... and " + (rows.size() - limit) + " more");
    }

    void listGroups(littlejlib.proj.Settings settings) {
        var store = new WorkspaceStore();
        var every = littlejlib.proj.workspace.WorkspaceRefresh.minutes(settings);
        for (var g : Groups.of(settings)) {
            var counted = g.isCode() ? "" : "  " + store.loadGroup(g.slug()).stream()
                    .filter(WorkspaceRecord::hasPrp).count() + " folders";
            line("  %-10s %-8s %s%s".formatted(g.slug(), g.groupKind().slug(), g.displayLabel(), counted));
            for (var p : g.paths()) {
                line("             %s%s".formatted(p,
                        java.nio.file.Files.isDirectory(java.nio.file.Path.of(p)) ? "" : "   (not reachable)"));
            }
            // A plain group refreshes itself, so when it last did is the only way to tell a quiet
            // list from a stopped one.
            if (!g.isCode()) line("             last scanned %s%s".formatted(
                    stamp(store.lastScanned(g.slug())),
                    every <= 0 ? ", automatic refresh off" : ", every " + every + " min"));
        }
    }

    static String stamp(long millis) {
        return millis <= 0 ? "never" : STAMP.format(Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault()));
    }

    static java.util.Map<String, Object> asMap(WorkspaceRecord r) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("path", r.path());
        m.put("name", r.name());
        m.put("relativePath", r.relativePath());
        m.put("group", r.groupSlug());
        m.put("prpCount", r.prpCount());
        m.put("hasPrp", r.hasPrp());
        m.put("lastActivity", r.activity());
        return m;
    }

    static String day(long millis) {
        return millis <= 0 ? "          "
                : DAY.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
}
