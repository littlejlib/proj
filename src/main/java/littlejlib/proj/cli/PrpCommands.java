package littlejlib.proj.cli;

import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PRPs from the command line.
 * <p>
 * Until now the only way to make one was to click through a JavaFX window, which meant the agent
 * working inside a repository could not create the very document that tells it what to work on -
 * it had to ask a human to open a dialog on its behalf. These are the same operations with no window
 * attached.
 * <p>
 * None of this touches the project index, so none of it cares whether the daemon is running: a PRP
 * is a file in a directory, and the directory is the answer.
 */
public final class PrpCommands {

    private PrpCommands() { }

    /**
     * The real console. NOT System.out: that is wrapped in a filter which drops any message
     * containing "javafx", "ArcadeDB" or "GraalVM", so a PRP with one of those words in its title
     * would silently fail to be listed. See LogConfig.out().
     */
    static final java.io.PrintStream OUT = littlejlib.proj.StdoutChannel.data();
    static final java.io.PrintStream ERR = littlejlib.proj.LogConfig.err();

    static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The prp folder to act on: --dir if given, otherwise ./prp under the current directory. */
    static Path prpDir(String dir) {
        var base = dir == null || dir.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(dir);
        var resolved = base.toAbsolutePath().normalize();
        // Pointing at the prp folder itself is the obvious mistake and costs nothing to absorb.
        return resolved.getFileName() != null && resolved.getFileName().toString().equals("prp")
                ? resolved : PrpCreator.prpDirOf(resolved);
    }

    @Command(name = "new", aliases = "create",
            description = "Create the next numbered PRP from the configured template")
    public static class NewCmd implements Runnable {

        @Parameters(arity = "1..*", description = "PRP name; several words are fine")
        List<String> name;

        @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder. Defaults to the current directory")
        String dir;

        @Option(names = "--index", description = "Force a PRP number instead of taking the next free one")
        Integer index;

        @Option(names = "--open", description = "Open the new file in the configured editor. Off by default, "
                + "because the caller is usually a script that is about to write the file itself")
        boolean open;

        @Option(names = {"--agent", "--claude"}, description = "Open a terminal at the PROJECT root "
                + "running the coding agent with this PRP as its instruction")
        boolean agent;

        @Option(names = "--force", description = "Create it even though a PRP of that name already exists")
        boolean force;

        @Option(names = "--json", description = "JSON instead of plain text")
        boolean json;

        @Override
        public void run() {
            var target = prpDir(dir);
            try {
                var created = PrpCreator.create(target, String.join(" ", name), index,
                        new SettingsManager().loadSettings(), force);

                if (json) {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("index", created.index());
                    m.put("name", created.name());
                    m.put("file", created.file().toString());
                    m.put("statusFile", PrpCreator.statusFileFor(target, created.index()).toString());
                    OUT.println(GSON.toJson(m));
                } else {
                    OUT.println(created.file());
                }

                if (open) {
                    littlejlib.proj.ui.PrpManagerActions
                            .openInEditor(created.file(), new SettingsManager().loadSettings());
                }
                if (agent) startAgent(created.file());
            } catch (java.nio.file.FileAlreadyExistsException e) {
                ERR.println("a PRP of that name already exists: " + e.getFile());
                ERR.println("pass --force to create another, or --index to place it deliberately");
                System.exit(4);
            } catch (IllegalArgumentException e) {
                ERR.println(e.getMessage());
                System.exit(2);
            } catch (IOException e) {
                ERR.println("could not create the PRP: " + e.getMessage());
                System.exit(3);
            }
        }
    }

    @Command(name = "list", aliases = "ls", description = "List the PRPs of a project")
    public static class ListCmd implements Runnable {

        @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
        String dir;

        @Option(names = "--open-only", description = "Only PRPs that are not closed")
        boolean openOnly;

        @Option(names = "--json", description = "JSON instead of tab separated")
        boolean json;

        @Override
        public void run() {
            var target = prpDir(dir);
            if (!Files.isDirectory(target)) {
                ERR.println("no prp folder at " + target);
                System.exit(4);
                return;
            }
            try {
                var rows = new ArrayList<Map<String, Object>>();
                for (var p : PrpCreator.list(target)) {
                    if (openOnly && p.getStatus() != null
                            && p.getStatus().name().equalsIgnoreCase("CLOSED")) continue;
                    rows.add(row(p));
                }

                if (json) {
                    OUT.println(GSON.toJson(rows));
                } else {
                    OUT.println("index\tstatus\tname\tstatusFile\tsubFiles\tfile");
                    for (var r : rows) {
                        OUT.println(r.get("index") + "\t" + r.get("status") + "\t" + r.get("name")
                                + "\t" + r.get("statusFile") + "\t" + r.get("subFileCount") + "\t" + r.get("file"));
                    }
                }
            } catch (IOException e) {
                ERR.println("could not read " + target + ": " + e.getMessage());
                System.exit(3);
            }
        }
    }

    @Command(name = "show", description = "Every file belonging to one PRP")
    public static class ShowCmd implements Runnable {

        @Parameters(arity = "1", description = "PRP index, e.g. 14 or 04")
        String index;

        @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
        String dir;

        @Option(names = "--json", description = "JSON instead of plain text")
        boolean json;

        @Override
        public void run() {
            var target = prpDir(dir);
            var wanted = String.format("%02d", Integer.parseInt(index.trim()));
            try {
                var hit = PrpCreator.list(target).stream()
                        .filter(p -> wanted.equals(p.getIndex())).findFirst().orElse(null);
                if (hit == null) {
                    ERR.println("no PRP " + wanted + " in " + target);
                    System.exit(4);
                    return;
                }
                if (json) {
                    OUT.println(GSON.toJson(row(hit)));
                } else {
                    OUT.println(hit.getMainFile());
                    if (hit.getStatusFile() != null) OUT.println(hit.getStatusFile());
                    hit.getSubFiles().forEach(OUT::println);
                }
            } catch (NumberFormatException e) {
                ERR.println("not a PRP index: " + index);
                System.exit(2);
            } catch (IOException e) {
                ERR.println("could not read " + target + ": " + e.getMessage());
                System.exit(3);
            }
        }
    }

    /**
     * Starting the agent on an existing PRP.
     * <p>
     * The manual version is: copy the relative path, open a terminal, navigate to the project, paste.
     * The step that goes wrong is the navigation - starting inside prp/ gives the agent a folder of
     * markdown and none of the code - so the working directory here is always the project root.
     */
    @Command(name = "agent", aliases = "claude",
            description = "Open a terminal at the project root running a coding agent on a PRP")
    public static class AgentCmd implements Runnable {

        @Parameters(arity = "1", description = "PRP index, e.g. 14")
        String index;

        @Option(names = {"-d", "--dir"}, description = "Project directory; defaults to the current directory")
        String dir;

        @Option(names = "--print", description = "Print what would run and exit, without opening a terminal")
        boolean dryRun;

        @Override
        public void run() {
            var project = projectDir(dir);
            try {
                var prp = littlejlib.proj.prp.AgentLauncher.findPrp(project, index);
                if (prp == null) {
                    ERR.println("no PRP " + index + " under " + PrpCreator.prpDirOf(project));
                    System.exit(4);
                    return;
                }
                if (dryRun) {
                    var s = new SettingsManager().loadSettings();
                    var plan = littlejlib.proj.prp.AgentLauncher.plan(prp, s);
                    // The terminal that will RUN it, which is not always the preferred one - naming
                    // the preferred terminal here when it cannot run a command would be a dry run
                    // that describes something other than what is about to happen.
                    var runner = littlejlib.proj.terminal.TerminalRunner.runner(s);
                    var preferred = littlejlib.proj.terminal.TerminalRunner.resolve(s);

                    OUT.println("directory  " + plan.projectDir());
                    OUT.println("prp        " + plan.relativePrp());
                    OUT.println("terminal   " + (runner == null ? "none available" : runner.getName())
                            + (runner != null && !runner.getName().equals(preferred.getName())
                                ? "   (" + preferred.getName() + " cannot run a command)" : ""));
                    OUT.println("command    " + plan.command());
                    return;
                }
                startAgent(prp);
            } catch (NumberFormatException e) {
                ERR.println("not a PRP index: " + index);
                System.exit(2);
            } catch (IOException e) {
                ERR.println("could not read the prp folder: " + e.getMessage());
                System.exit(3);
            }
        }
    }

    static void startAgent(Path prpFile) {
        var settings = new SettingsManager().loadSettings();
        var plan = littlejlib.proj.prp.AgentLauncher.plan(prpFile, settings);
        if (littlejlib.proj.prp.AgentLauncher.launch(prpFile, settings)) {
            OUT.println("started in " + plan.projectDir() + ":  " + plan.command());
        } else {
            ERR.println("could not start a terminal - register one with 'proj settings'");
            System.exit(3);
        }
    }

    /** The PROJECT directory, given something that may be the project or its prp folder. */
    static Path projectDir(String dir) {
        var base = dir == null || dir.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(dir);
        var resolved = base.toAbsolutePath().normalize();
        return resolved.getFileName() != null && resolved.getFileName().toString().equals("prp")
                && resolved.getParent() != null ? resolved.getParent() : resolved;
    }

    @Command(name = "dir", description = "Print the prp folder that these commands would act on")
    public static class DirCmd implements Runnable {

        @Option(names = {"-d", "--dir"}, description = "Project directory")
        String dir;

        @Override
        public void run() {
            OUT.println(prpDir(dir));
        }
    }

    static Map<String, Object> row(PrpEntry p) {
        var m = new LinkedHashMap<String, Object>();
        m.put("index", p.getIndex());
        m.put("status", p.getStatus() == null ? "" : p.getStatus().name().toLowerCase());
        m.put("name", p.getName());
        m.put("file", p.getMainFile() == null ? "" : p.getMainFile().toString());
        m.put("statusFile", p.getStatusFile() == null ? "" : p.getStatusFile().toString());
        m.put("subFileCount", p.getSubFileCount());
        m.put("subFiles", p.getSubFiles().stream().map(Path::toString).toList());
        return m;
    }
}
