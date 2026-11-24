package littlejlib.proj.cli;

import picocli.CommandLine;

import java.io.PrintWriter;

/**
 * What every picocli root in proj gets: {@code -h/--help} and {@code -V/--version} on every command in the tree,
 * output to the real console (System.out and System.err are filtered by LogConfig and drop what a Writer writes),
 * and a wrong option answered with one line naming it plus where to get help.
 */
public final class CliWiring {

    private CliWiring() { }

    public static CommandLine wire(CommandLine root, PrintWriter out, PrintWriter err) {
        root.getCommandSpec().name("proj");
        root.getCommandSpec().version(versionLine());
        addHelp(root);
        root.setOut(out);
        root.setErr(err);
        root.setParameterExceptionHandler(CliWiring::onBadInput);
        if (littlejlib.proj.LaunchMode.stdoutCaptured())
            root.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
        return root;
    }

    /** Standard I/O of the real console. */
    public static CommandLine wire(CommandLine root) {
        return wire(root, new PrintWriter(littlejlib.proj.StdoutChannel.data(), true),
                new PrintWriter(littlejlib.proj.LogConfig.err(), true));
    }

    static String versionLine() {
        var v = jarrunner.jr.apputils.JrApp.appVersion().filter(s -> !s.isBlank()).map(s -> s + " ").orElse("");
        return "proj " + v + "(build " + littlejlib.proj.daemon.Build.STAMP + ")";
    }

    static void addHelp(CommandLine c) {
        var spec = c.getCommandSpec();
        // a command that forwards its arguments (@Unmatched) lets the receiving command answer --help
        var forwards = !spec.unmatchedArgsBindings().isEmpty();
        if (!forwards && !spec.mixinStandardHelpOptions() && spec.findOption("--help") == null && spec.findOption("-h") == null
                && spec.findOption("--version") == null && spec.findOption("-V") == null) {
            try {
                spec.mixinStandardHelpOptions(true);
                if (spec.version().length == 0) spec.version(versionLine());
            } catch (CommandLine.PicocliException ignored) {
                // an option clashes with -h or -V; that command keeps what it has
            }
        }
        for (var sub : c.getSubcommands().values()) addHelp(sub);
    }

    static int onBadInput(CommandLine.ParameterException ex, String[] args) {
        var cmd = ex.getCommandLine();
        var err = cmd.getErr();
        err.println("proj: " + ex.getMessage());
        CommandLine.UnmatchedArgumentException.printSuggestions(ex, err);
        err.println("Try '" + cmd.getCommandSpec().qualifiedName(" ") + " --help' for its options.");
        err.flush();
        var code = cmd.getCommandSpec().exitCodeOnInvalidInput();
        return code;
    }
}
