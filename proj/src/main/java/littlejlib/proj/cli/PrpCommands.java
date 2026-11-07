package littlejlib.proj.cli;

import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.prp.AgentExe;
import littlejlib.proj.prp.AgentLauncher;
import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpLogExport;
import littlejlib.proj.prp.PrpLogs;
import littlejlib.proj.prp.PrpFileEdit;
import littlejlib.proj.prp.PrpInstructions;
import littlejlib.proj.prp.PrpPrompt;

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

        @Option(names = "--model", paramLabel = "<model>",
                description = "With --agent: the model the agent runs on (haiku, sonnet, opus or a full model id)")
        String model;

        @Option(names = "--exe", paramLabel = "<name>",
                description = "With --agent: which claude to run, by bare name (fclaude, pclaude ...), found on PATH")
        String exe;

        @Option(names = "--force", description = "Create it even though a PRP of that name already exists")
        boolean force;

        @Option(names = "--json", description = "JSON instead of plain text")
        boolean json;

        @Override
        public void run() {
            var target = prpDir(dir);
            try {
                // Before the PRP exists: a bad model must not leave behind a PRP that was meant to start an agent.
                if (model != null && !model.isBlank() && !agent) {
                    ERR.println("--model needs --agent");
                    System.exit(2);
                    return;
                }
                littlejlib.proj.prp.PrpPrompt.modelFlag(model);
                if (exe != null && !exe.isBlank() && !agent) {
                    ERR.println("--exe needs --agent");
                    System.exit(2);
                    return;
                }
                exe = AgentExe.require(exe);
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
                if (agent) startAgent(created.file(), false, model, exe);
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
    @Command(name = "agent", aliases = "claude", mixinStandardHelpOptions = true,
            description = "Open a terminal at the project root running a coding agent on a PRP")
    public static class AgentCmd implements Runnable {

        @Parameters(arity = "1", description = "PRP index, e.g. 14")
        String index;

        @Option(names = {"-d", "--dir"}, description = "Project directory; defaults to the current directory")
        String dir;

        @Option(names = "--model", paramLabel = "<model>",
                description = "Model the agent runs on: haiku, sonnet, opus or a full model id. "
                        + "Without it the agent takes the default of the user's claude settings")
        String model;

        @Option(names = "--exe", paramLabel = "<name>",
                description = "Which claude to run, by bare name: fclaude, pclaude ... Found on PATH, so no "
                        + ".cmd, .bat, .exe or .sh. For this launch only; without it the usual claude runs")
        String exe;

        @Option(names = "--print", description = "Print what would run and exit, without opening a terminal")
        boolean dryRun;

        @Option(names = {"--new-terminal", "--new-tab"},
                description = "Open another terminal instead of running here. Default when there is "
                        + "no console to run in, which is every launch from the picker")
        boolean newTerminal;

        @Override
        public void run() {
            var project = projectDir(dir);
            try {
                exe = AgentExe.require(exe);
                var prp = littlejlib.proj.prp.AgentLauncher.findPrp(project, index);
                if (prp == null) {
                    ERR.println("no PRP " + index + " under " + PrpCreator.prpDirOf(project));
                    System.exit(4);
                    return;
                }
                if (dryRun) {
                    var s = new SettingsManager().loadSettings();
                    var plan = AgentLauncher.planWith(prp, s, model, exe);
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
                    OUT.println("tab        " + plan.tabName());
                    OUT.println("model      " + (model == null || model.isBlank() ? "(settings default)" : model.trim()));
                    OUT.println("exe        " + (exe == null ? "(template default)"
                            : exe + "  ->  " + AgentExe.resolve(exe)));
                    OUT.println("runs in    "
                            + (littlejlib.proj.terminal.TerminalRunner.startsHere(newTerminal)
                            ? "this terminal" : "a new " + (runner == null ? "terminal" : runner.getName()) + " tab"));
                    OUT.println("command    " + plan.command());
                    return;
                }
                startAgent(prp, newTerminal, model, exe);
            } catch (NumberFormatException e) {
                ERR.println("not a PRP index: " + index);
                System.exit(2);
            } catch (IllegalArgumentException e) {
                ERR.println(e.getMessage());
                System.exit(2);
            } catch (IOException e) {
                ERR.println("could not read the prp folder: " + e.getMessage());
                System.exit(3);
            }
        }
    }

    static void startAgent(Path prpFile) {
        startAgent(prpFile, false, null);
    }

    static void startAgent(Path prpFile, boolean newTerminal) {
        startAgent(prpFile, newTerminal, null);
    }

    /**
     * @param newTerminal open another terminal even though this one would do
     * @param model      haiku, sonnet, opus or a model id; null leaves it to the user's settings
     */
    static void startAgent(Path prpFile, boolean newTerminal, String model) {
        startAgent(prpFile, newTerminal, model, null);
    }

    /** @param exe an explicit program (fclaude ...) instead of the template's own; null keeps it */
    static void startAgent(Path prpFile, boolean newTerminal, String model, String exe) {
        var settings = new SettingsManager().loadSettings();
        var plan = AgentLauncher.planWith(prpFile, settings, model, exe);

        // Recorded BEFORE anything runs. A session that was never recorded cannot be resumed, and
        // the queued case leaves no moment afterwards: the shell runs the command once we exit.
        littlejlib.proj.prp.PrpSessions.record(plan);

        switch (littlejlib.proj.prp.AgentLauncher.start(plan, settings, null, newTerminal)) {
            // The shell echoes the command itself as it runs it, so saying it again here is noise.
            case QUEUED -> ERR.println("starting in this terminal - proj prp agent "
                    + PrpPrompt.indexOf(prpFile) + " --new-terminal opens a tab instead");
            case RAN -> OUT.println("started in " + plan.projectDir() + ":  " + plan.command());
            case OPENED_ONLY -> OUT.println("opened a shell in " + plan.projectDir()
                    + " - the command is on the clipboard");
            case FAILED -> {
                ERR.println("could not start a terminal - register one with 'proj settings'");
                System.exit(3);
            }
        }
    }

    /** The PROJECT directory, given something that may be the project or its prp folder. */
    static Path projectDir(String dir) {
        var base = dir == null || dir.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(dir);
        var resolved = base.toAbsolutePath().normalize();
        return resolved.getFileName() != null && resolved.getFileName().toString().equals("prp")
                && resolved.getParent() != null ? resolved.getParent() : resolved;
    }

    /**
     * The instruction a session on this PRP should open with, printed rather than launched.
     * <p>
     * Same text the picker's agent launcher sends: the PRP and its status file with an {@code @} so
     * they are read, and the previous session's log named WITHOUT one so the tail is read on request
     * rather than the whole file being pulled into the context. Every clause is conditional on its
     * file existing - see {@link PrpPrompt}.
     */
    @Command(name = "prompt", aliases = "ask",
            description = "Print the claude-code instruction for a PRP (@prp file, @status file, previous log)")
    public static class PromptCmd implements Runnable {

        @Parameters(arity = "0..1", description = "PRP index, e.g. 29. Defaults to the newest open PRP")
        String index;

        @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
        String dir;

        @Option(names = "--command", description = "The whole command line, not just the instruction")
        boolean command;

        @Option(names = "--percent", description = "How much of the previous log's tail to ask for (default from settings)")
        Integer percent;

        @Override
        public void run() {
            var target = prpDir(dir);
            var entry = resolve(target, index);
            if (entry == null) return;
            var settings = new littlejlib.proj.SettingsManager().loadSettings();
            var pct = percent != null ? percent
                    : settings == null ? 10 : settings.getAgentLogsTailPercent();
            OUT.println(command
                    ? PrpPrompt.command(entry.getMainFile(), settings, pct)
                    : PrpPrompt.of(entry.getMainFile(), settings, pct));
        }
    }

    /**
     * proj's own {@code /export}, which claude-code can actually run because it is not a slash
     * command: the session in this directory, written into the PRP folder as
     * {@code NN-prp.99.SS.logs.txt} with the serial worked out from what is already there.
     */
    @Command(name = "logs", aliases = {"export", "export-logs"},
            description = "Export this directory's claude-code session as the PRP's next log file")
    public static class LogsCmd implements Runnable {

        @Parameters(arity = "0..1", description = "PRP index, e.g. 29. Defaults to the newest open PRP")
        String index;

        @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
        String dir;

        @Option(names = "--session", description = "Session id or prefix. Defaults to the latest session of this directory")
        String session;

        @Option(names = "--result-lines", description = "Lines of each tool result to keep (default 20). "
                + "0 keeps all of them, same as --full")
        int resultLines = 20;

        @Option(names = "--full", description = "Keep every line of every tool result. Bigger file, nothing elided")
        boolean full;

        @Option(names = "--dry-run", description = "Say what would be written and where, and write nothing")
        boolean dryRun;

        @Override
        public void run() {
            var target = prpDir(dir);
            var entry = resolve(target, index);
            if (entry == null) return;
            var cwd = Path.of(System.getProperty("user.dir"));
            try {
                var file = session == null || session.isBlank() ? null : oneSession(session);
                if (file == null && session != null && !session.isBlank()) return;
                if (dryRun) {
                    var chosen = file != null ? file : PrpLogExport.latestSession(cwd);
                    OUT.println(target.resolve(PrpLogs.nextName(target, entry.getIndex()))
                            + "\t" + chosen);
                    return;
                }
                var w = PrpLogExport.write(target, entry, cwd, file, full ? 0 : resultLines);
                OUT.println(w.file().toString());
                ERR.println("wrote " + w.turns() + " entries (" + w.bytes() / 1024 + " KB) from session "
                        + w.session().getFileName());
                // Exporting a log is also the moment the session becomes worth resuming, and both
                // facts are already in hand here - so a session started by hand ends up recorded
                // without anybody having to think about it.
                remember(w, entry, target);
            } catch (RuntimeException e) {
                ERR.println(e.getMessage());
                System.exit(3);
            }
        }

        /** Only if it is not already recorded: an existing row carries the tab name that was used. */
        private void remember(PrpLogExport.Written w, PrpEntry entry, Path prpDir) {
            var name = w.session().getFileName().toString();
            var id = name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name;
            var project = prpDir.getParent() == null ? prpDir : prpDir.getParent();
            if (littlejlib.proj.prp.PrpSessions.of(entry.getMainFile()).stream()
                    .anyMatch(s -> id.equals(s.sessionId()))) return;
            littlejlib.proj.prp.PrpSessions.record(id, entry.getMainFile(), entry.getIndex(),
                    project, littlejlib.proj.prp.PrpSessions.tabName(entry.getMainFile()));
        }

        private Path oneSession(String idOrPrefix) {
            var found = littlejlib.proj.cct.export.SessionRead.byId(idOrPrefix);
            if (found.isEmpty()) {
                ERR.println("no session matches " + idOrPrefix);
                System.exit(4);
                return null;
            }
            if (found.size() > 1) {
                ERR.println(found.size() + " sessions start with that prefix; be more specific");
                found.forEach(p -> ERR.println("  " + p.getFileName()));
                System.exit(2);
                return null;
            }
            return found.get(0);
        }
    }

    /**
     * Collecting the instructions the user gave in CONVERSATION and putting them into the PRP.
     * <p>
     * The PRP says what to build; the conversation says what to build INSTEAD, and by the third
     * cycle nobody remembers where the corrections were. This puts them where the next cycle will
     * read them. It only ever inserts, it keeps a backup first, and anything already written into
     * the PRP by hand is skipped - so running it twice converges rather than doubling.
     */
    @Command(name = "instructions", aliases = {"instr", "user-instructions"},
            description = "Collect the instructions given in conversation and insert them into the PRP")
    public static class InstructionsCmd implements Runnable {

        @Parameters(arity = "0..1", description = "PRP index, e.g. 29. Defaults to the newest open PRP")
        String index;

        @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
        String dir;

        @Option(names = "--session", description = "Session id or prefix. Defaults to this directory's latest")
        String session;

        @Option(names = "--all-sessions", description = "Every session of this directory that opened on this PRP")
        boolean allSessions;

        @Option(names = "--dry-run", description = "Print the block that would be inserted, and change nothing")
        boolean dryRun;

        @Option(names = "--stdout", description = "Print the block instead of touching the PRP")
        boolean toStdout;

        @Override
        public void run() {
            var target = prpDir(dir);
            var entry = resolve(target, index);
            if (entry == null) return;
            var project = target.getParent() == null ? target : target.getParent();
            try {
                var sessions = pickSessions(project, entry);
                if (sessions.isEmpty()) { ERR.println("no session found to collect from"); System.exit(4); }
                // Our own blocks are cut out first, or the second run would find the instructions it
                // wrote itself and conclude there was nothing new.
                var prpText = PrpFileEdit.withoutBlocks(Files.readString(entry.getMainFile()));
                var wrote = 0;
                for (var s : sessions) wrote += one(entry, s, prpText, wrote == 0);
                if (wrote == 0) ERR.println("nothing new - every instruction is already in the PRP");
            } catch (IOException e) {
                ERR.println("could not read or write " + entry.getMainFile() + ": " + e.getMessage());
                System.exit(3);
            }
        }

        private List<Path> pickSessions(Path project, PrpEntry entry) {
            if (session != null && !session.isBlank()) {
                var found = littlejlib.proj.cct.export.SessionRead.byId(session);
                if (found.size() > 1) {
                    ERR.println(found.size() + " sessions start with that prefix; be more specific");
                    System.exit(2);
                }
                return found;
            }
            if (allSessions) return PrpInstructions.sessionsFor(project, entry.getIndex());
            return List.of(PrpLogExport.latestSession(project));
        }

        /** @return 1 when something was written, 0 when there was nothing new in this session */
        private int one(PrpEntry entry, Path sessionFile, String prpText, boolean firstWrite)
                throws IOException {
            var found = PrpInstructions.collect(sessionFile);
            var keep = PrpInstructions.notAlreadyIn(found.entries(), prpText);
            var id = PrpInstructions.sessionIdOf(sessionFile);
            if (found.skippedMachine() > 0)
                ERR.println(found.skippedMachine() + " turn(s) from other sessions or the harness skipped in " + id);
            if (keep.isEmpty()) return 0;
            var block = PrpInstructions.block(found, keep, id);
            if (toStdout || dryRun) {
                OUT.println(block);
                ERR.println((dryRun ? "would insert " : "") + keep.size() + " instruction(s) from "
                        + id + " into " + entry.getMainFile().getFileName());
                return 0;
            }
            if (firstWrite) ERR.println("backup: " + PrpFileEdit.backup(entry.getMainFile()));
            var current = Files.readString(entry.getMainFile());
            var result = PrpFileEdit.insert(current, block,
                    littlejlib.proj.cct.export.Stamp.of(found.from()), id);
            PrpFileEdit.write(entry.getMainFile(), result.text());
            OUT.println(entry.getMainFile().toString());
            ERR.println(keep.size() + " instruction(s) from " + id + " - " + result.what());
            return 1;
        }
    }

    /** The PRP a verb should act on, with the same message whichever verb asked. */
    static PrpEntry resolve(Path prpDir, String index) {
        if (!Files.isDirectory(prpDir)) {
            ERR.println("no prp folder at " + prpDir);
            System.exit(4);
            return null;
        }
        try {
            var entry = PrpLogExport.pick(PrpCreator.list(prpDir), index,
                    Path.of(System.getProperty("user.dir")));
            if (entry == null) {
                ERR.println(index == null ? "no PRPs in " + prpDir : "no PRP " + index + " in " + prpDir);
                System.exit(4);
            }
            return entry;
        } catch (NumberFormatException e) {
            ERR.println("not a PRP index: " + index);
            System.exit(2);
            return null;
        } catch (IOException e) {
            ERR.println("could not read " + prpDir + ": " + e.getMessage());
            System.exit(3);
            return null;
        }
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
