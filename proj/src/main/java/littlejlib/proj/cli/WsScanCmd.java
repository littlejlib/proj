package littlejlib.proj.cli;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.group.Groups;
import littlejlib.proj.group.ProjectGroup;
import littlejlib.proj.workspace.WorkspaceRefresh;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static littlejlib.proj.cct.cli.Out.line;
import static littlejlib.proj.cct.cli.Out.note;

/**
 * Re-walks the plain groups and rewrites their rows.
 */
@Command(name = "scan", description = "Re-walk the plain groups and rebuild their rows")
public final class WsScanCmd implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "Group slug; all plain groups when omitted")
    String group;

    @Option(names = "--depth", description = "Override the group's configured depth")
    Integer depth;

    @Option(names = "--if-stale",
            description = "Only walk a group whose last scan is older than workspaceRefreshMinutes")
    boolean ifStale;

    @Option(names = "--json", description = "JSON instead of plain text")
    boolean json;

    @Override
    public void run() {
        var settings = new SettingsManager().loadSettings();
        var groups = group == null || group.isBlank()
                ? Groups.plain(settings)
                : java.util.stream.Stream.of(Groups.bySlug(settings, group))
                        .filter(java.util.Objects::nonNull).filter(g -> !g.isCode()).toList();

        if (groups.isEmpty()) {
            note(group == null
                    ? "no plain groups configured - see 'proj config set group.<slug>.paths'"
                    : "no plain group called '" + group + "'");
            return;
        }
        var minutes = WorkspaceRefresh.minutes(settings);
        var rows = new java.util.LinkedHashMap<String, Object>();
        var t0 = System.currentTimeMillis();
        var walked = 0;

        for (var g : groups) {
            if (ifStale && !WorkspaceRefresh.due(g, minutes)) {
                if (!json) note("  fresh, left alone: " + g.slug());
                continue;
            }
            walked++;
            rows.put(g.slug(), scan(g).size());
        }

        var ms = System.currentTimeMillis() - t0;
        if (json) {
            line(QueryCommands.GSON.toJson(java.util.Map.of("groups", rows, "ms", ms)));
            return;
        }
        rows.forEach((slug, count) -> line("  " + count + "  " + slug));
        line("scanned " + walked + " group" + (walked == 1 ? "" : "s") + " in " + ms + " ms");
    }

    /**
     * Forced, always: somebody typed this. The cautious form - which leaves the index alone when a
     * walk comes back suspiciously empty - is the automatic one, and it is not what a person asking
     * for a scan by name means.
     */
    java.util.List<littlejlib.proj.workspace.WorkspaceRecord> scan(ProjectGroup g) {
        if (depth != null) g.depth(depth);
        return WorkspaceRefresh.run(g, true, null, json ? null : n -> note("  " + n));
    }
}
