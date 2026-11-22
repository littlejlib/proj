package littlejlib.proj.cli;

import littlejlib.proj.claude.ClaudeSetup;
import littlejlib.proj.track.Waiting;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

/** {@code proj claude}: proj's own short pointer and hook inside Claude Code. */
@Command(name = "claude", mixinStandardHelpOptions = true,
        description = {"proj's place in Claude Code: ~/.claude/proj.md (imported by one @proj.md line in ~/.claude/CLAUDE.md)",
                "and a SessionStart hook that points a session at `proj prp guide` when its folder has a prp/ folder.",
                "Everything about PRPs is in `proj prp guide`; this only tells a new session that proj exists."},
        subcommands = {ClaudeCli.Install.class, ClaudeCli.Status.class, ClaudeCli.Hook.class})
public class ClaudeCli implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(name = "install", description = "Write or update ~/.claude/proj.md, its @proj.md import and the SessionStart hook; --undo removes them")
    public static class Install implements Runnable {

        @Option(names = "--undo", description = "Remove what install added (backups of CLAUDE.md and settings.json are kept beside them)")
        boolean undo;

        @Override
        public void run() {
            TrackCli.run(() -> {
                var done = undo ? ClaudeSetup.uninstall() : ClaudeSetup.install();
                if (done.isEmpty()) PrpCommands.OUT.println(undo ? "nothing to remove" : "already current");
                done.forEach(PrpCommands.OUT::println);
            });
        }
    }

    @Command(name = "status", description = "Say whether proj's files in ~/.claude are installed and current. Exit 1 when not")
    public static class Status implements Runnable {

        @Override
        public void run() {
            TrackCli.run(() -> {
                var problems = ClaudeSetup.status();
                if (problems.isEmpty()) PrpCommands.OUT.println("installed and current");
                problems.forEach(PrpCommands.OUT::println);
                if (!problems.isEmpty()) System.exit(1);
            });
        }
    }

    @Command(name = "hook", description = "Called by Claude Code's SessionStart hook; prints the pointer to the PRP guide")
    public static class Hook implements Runnable {

        @Parameters(arity = "0..1", description = "session-start")
        String event;

        @Override
        public void run() {
            PrpCommands.OUT.println("This project keeps PRPs (requirement documents) in prp/. Before any work on a PRP, run `proj prp guide` and follow it. "
                    + "Never run `proj prp agent` without the user's yes for that PRP.");
            try {
                var dir = System.getenv("CLAUDE_PROJECT_DIR");
                var project = Path.of(dir == null || dir.isBlank() ? System.getProperty("user.dir") : dir);
                var waiting = Waiting.open(project.resolve("prp"), null).stream().filter(Waiting::forUser).count();
                if (waiting > 0) PrpCommands.OUT.println(waiting + " work items in this project wait for the user (questions to ask, points to discuss): "
                        + "`proj prp plan list -t confirm` and `-t discuss`. Raise them when they bear on the work.");
            } catch (Exception ignored) {
                // a hook must never stop a session from starting
            }
        }
    }
}
