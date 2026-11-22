package littlejlib.proj.cli;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.prp.PrpGuide;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Prints the PRP conventions a session must follow; the one line in every new PRP points here. */
@Command(name = "guide", aliases = "conventions",
        description = {"Print the PRP conventions (what every PRP's first lines point to).",
                "Sources: the copy inside proj, then the global list (setting prpGuide), then the project's list (prp/proj.json prpGuide), in order.",
                "An entry is 'extend:<source>' (printed after what came before) or 'replace:<source>' (instead of all of it; no prefix = replace).",
                "A source is 'bundled', an http(s) url, a local file, or a git file for private repositories, fetched over SSH:",
                "  git@github.com:org/repo.git#ref:path/file.md   or   git+https://github.com/org/repo/blob/ref/path/file.md",
                "Urls and git files are read from a LOCAL COPY in ~/littlejlib/proj/githubcache, refreshed after a day or with --refresh;",
                "when the network or git fails the copy is used. The last lines name every source and its local copy."})
public class PrpGuideCmd implements Runnable {

    @Option(names = "--refresh", description = "Fetch online and git sources again now instead of using their local copies")
    boolean refresh;

    @Option(names = "--source", description = "Only print the lines that name the sources and their local copies")
    boolean source;

    @Option(names = {"-d", "--dir"}, description = "Project directory (its prp/proj.json may add its own guide sources)")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var g = PrpGuide.load(PrpCommands.projectDir(dir), new SettingsManager().loadSettings(), refresh);
            if (!source) {
                PrpCommands.OUT.print(g.text());
                PrpCommands.OUT.println();
            }
            g.origins().forEach(PrpCommands.OUT::println);
        });
    }
}
