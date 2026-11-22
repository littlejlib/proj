package littlejlib.proj.cli;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.group.Groups;
import littlejlib.proj.track.Budget;
import littlejlib.proj.track.GlobalFind;
import littlejlib.proj.track.GlobalIndex;
import littlejlib.proj.track.GlobalLine;
import littlejlib.proj.track.Jsonl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds a PRP on this machine by uuid, ticket (key-NN), number or words, across every group proj knows.
 * The cache ({@code prp-global.jsonl} in proj's folder) is rebuilt when older than --max-age, when asked,
 * or when a uuid's cached location no longer holds it (the PRP moved or was renamed).
 */
@Command(name = "find", description = "Find a PRP anywhere: by uuid (or its first 6+ symbols), key-NN, number, or words")
public class PrpFindCmd implements Runnable {

    @Parameters(arity = "0..*", description = "What to look for. None: just refresh the cache")
    List<String> query;

    @Option(names = "--rescan", description = "Walk the folders again before searching")
    boolean rescan;

    @Option(names = "--max-age", description = "Rebuild the cache first if it is older than this many minutes (default: use it as it is)")
    long maxAge = -1;

    @Option(names = "--root", description = "Also walk this folder (repeatable), with --depth levels of projects below it")
    List<Path> roots;

    @Option(names = "--depth", description = "With --root: how many levels below it projects may sit (default 2)")
    int depth = 2;

    @Option(names = "--open", description = "Only PRPs that are not closed")
    boolean openOnly;

    @Option(names = "--limit", description = "At most this many hits (default 30)")
    int limit = 30;

    @Option(names = "--json", description = "The hits as json lines")
    boolean json;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var q = query == null ? "" : String.join(" ", query).strip();
            var age = GlobalIndex.ageMinutes();
            var all = fresh(rescan || roots != null || age == Long.MAX_VALUE || (maxAge >= 0 && age > maxAge));
            var hits = q.isEmpty() ? List.<GlobalLine>of() : GlobalFind.find(all, q);
            if (!q.isEmpty() && !hits.isEmpty() && hits.stream().anyMatch(h -> !GlobalFind.stillThere(h)))
                hits = GlobalFind.find(all = fresh(true), q);
            if (openOnly) hits = hits.stream().filter(h -> !"closed".equals(h.state()) && !"cancelled".equals(h.state())).toList();
            if (q.isEmpty()) {
                PrpCommands.OUT.println(all.size() + " PRPs in " + GlobalIndex.file());
                return;
            }
            print(hits);
            if (!rescan && age != Long.MAX_VALUE) PrpCommands.ERR.println("(cache " + age + " minutes old; --rescan walks the folders again)");
        });
    }

    List<GlobalLine> fresh(boolean rebuild) throws java.io.IOException {
        if (!rebuild) return GlobalIndex.read();
        var extra = new ArrayList<GlobalIndex.Root>();
        if (roots != null) for (var r : roots) extra.add(new GlobalIndex.Root(r, depth));
        return GlobalIndex.rebuild(extra);
    }

    void print(List<GlobalLine> hits) {
        if (json) {
            hits.stream().limit(limit).forEach(h -> PrpCommands.OUT.println(Jsonl.GSON.toJson(h)));
            return;
        }
        if (hits.isEmpty()) {
            PrpCommands.OUT.println("no PRP matches");
            return;
        }
        for (var h : hits.stream().limit(limit).toList()) {
            var ticket = (h.key() == null ? "" : h.key() + "-") + h.number();
            PrpCommands.OUT.println(ticket + "  [" + h.state() + (h.progress() == null ? "" : ", " + h.progress()) + "]  "
                    + h.name() + "  " + (h.uuid() == null || h.uuid().isEmpty() ? "(no uuid)" : h.uuid()));
            PrpCommands.OUT.println("    " + h.file());
            if (h.summary() != null) PrpCommands.OUT.println("    " + Budget.cut(h.summary(), 160));
        }
        if (hits.size() > limit) PrpCommands.OUT.println("(" + (hits.size() - limit) + " more; --limit)");
    }
}
