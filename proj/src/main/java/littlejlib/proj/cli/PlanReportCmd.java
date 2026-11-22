package littlejlib.proj.cli;

import littlejlib.proj.report.Jq;
import littlejlib.proj.report.ReportBuilder;
import littlejlib.proj.report.ReportQuery;
import littlejlib.proj.report.ReportText;
import littlejlib.proj.track.WorkGraph;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.LinkedHashMap;
import java.util.List;

/** The work items of one or several projects as one graph: what waits on the user, what can start, the path to a goal. */
@Command(name = "report", description = {
        "The work plan of one or several projects as one graph, computed from the files (no AI).",
        "Agents: --format json --jq '<program>' filters inside proj, as gh --jq does; --format md for reading.",
        "Example: proj prp plan report --with billing,ledger --goal billing:31 --format json --jq '.items[] | select(.startNow) | .key'"})
public class PlanReportCmd implements Runnable {

    @Option(names = "--with", description = "Projects: short name, alias, uuid or path; comma separated. Default: this project")
    List<String> with;

    @Option(names = "--no-follow", description = "Do not add the projects that items point at")
    boolean noFollow;

    @Option(names = "--prp", description = "Only items owned by or related to these PRPs: ticket (billing-7), uuid or was:<old>")
    List<String> prp;

    @Option(names = "--goal", description = "The goal item for the path: Project:id or Project#id")
    String goal;

    @Option(names = "--tag", description = "Only these importances: critical, infra, extra")
    List<String> tag;

    @Option(names = "--done", description = "Include done and dropped items")
    boolean done;

    @Option(names = "--panel", description = "md only: waiting, ready, doing, path, resources, problems, loose")
    List<String> panel;

    @Option(names = "--format", defaultValue = "md", description = "md (default), json, jsonl (one item per line), html (read-only, for a record or a test; the live page is the daemon's)")
    String format;

    @Option(names = "--jq", description = "A jq program applied to the json; strings print raw, one result per line")
    String jq;

    @Option(names = "--pretty", description = "Indent the json")
    boolean pretty;

    @Option(names = "--url", description = "Print the live page's address for this scope (served by the daemon)")
    boolean url;

    @Option(names = "--open", description = "Open the live page in the browser")
    boolean open;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var params = new LinkedHashMap<String, String>();
            if (with != null) params.put("with", String.join(",", with));
            if (noFollow) params.put("follow", "0");
            if (prp != null) params.put("prp", String.join(",", prp));
            if (goal != null) params.put("goal", goal);
            if (tag != null) params.put("tag", String.join(",", tag));
            if (done) params.put("done", "1");
            if (panel != null) params.put("panel", String.join(",", panel));
            var q = ReportQuery.of(params);
            var here = with == null ? PrpCommands.prpDir(dir).getParent() : null;
            if (url || open) {
                if (with == null) params.put("with", littlejlib.proj.track.ProjectScope.resolve(here.toString()).name());
                var port = new littlejlib.proj.SettingsManager().loadSettings().getReportPort();
                var link = "http://127.0.0.1:" + port + littlejlib.proj.report.ReportPage.url("/report", ReportQuery.of(params).params());
                PrpCommands.OUT.println(link);
                try (var s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port), 500);
                } catch (java.io.IOException e) {
                    PrpCommands.ERR.println("nothing answers on port " + port + ": start the daemon (proj daemon start), or check reportPort in settings");
                    return;
                }
                if (open) new ProcessBuilder(System.getProperty("os.name").startsWith("Windows")
                        ? List.of("cmd", "/c", "start", "", link) : List.of("open", link)).start();
                return;
            }
            var data = ReportBuilder.build(q, here, WorkGraph::readPrps);
            if (jq != null) {
                for (var line : Jq.run(ReportText.json(data, false), jq)) PrpCommands.OUT.println(line);
                return;
            }
            PrpCommands.OUT.print(switch (format) {
                case "json" -> ReportText.json(data, pretty) + "\n";
                case "jsonl" -> ReportText.jsonl(data);
                case "md" -> ReportText.markdown(data, q.panels());
                case "html" -> littlejlib.proj.report.ReportPage.render(data, q, null);
                default -> throw new IllegalArgumentException("format is md, json or jsonl");
            });
        });
    }
}
