package littlejlib.proj.cli;

import com.google.gson.GsonBuilder;
import littlejlib.proj.cct.export.Stamp;
import littlejlib.proj.hib.HibRecord;
import littlejlib.proj.hib.HibStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static littlejlib.proj.cli.PrpCommands.OUT;

@Command(name = "parked", aliases = {"hibernated", "list-hibernated"}, mixinStandardHelpOptions = true,
        description = "Hibernated conversations, most recent first. --was for restored ones, --all for both")
public final class HibParkedCmd implements Runnable {

    @Parameters(arity = "0..1", description = "Only those whose name, session id, folder or PRP contains this")
    String filter;

    @Option(names = "--was", description = "Show only conversations that were hibernated and have been restored")
    boolean was;

    @Option(names = "--all", description = "Show hibernated and restored")
    boolean all;

    @Option(names = "--json", description = "One JSON array of the records")
    boolean json;

    @Override
    public void run() {
        var rows = HibStore.all().stream()
                .filter(r -> all || (was ? !r.hibernated() : r.hibernated()))
                .filter(this::matches).toList();
        if (json) {
            OUT.println(new GsonBuilder().setPrettyPrinting().create().toJson(rows));
            return;
        }
        for (var r : rows) {
            OUT.println((r.hibernated() ? "zz  " : "was ") + Stamp.of(nz(r.hibernatedAt())) + "  "
                    + r.label() + "  " + r.prpLabel() + "  " + r.sessionId().substring(0, 8));
            OUT.println("      " + r.cwd());
        }
        OUT.println(rows.size() + (was ? " restored" : all ? " records" : " hibernated")
                + " - restore with  proj hib restore <name>   (" + HibStore.file() + ")");
    }

    boolean matches(HibRecord r) {
        if (filter == null || filter.isBlank()) return true;
        var f = filter.toLowerCase();
        return (r.label() + " " + r.sessionId() + " " + r.cwd() + " " + r.prpLabel()).toLowerCase().contains(f);
    }

    static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
