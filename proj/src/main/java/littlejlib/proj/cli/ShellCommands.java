package littlejlib.proj.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import littlejlib.proj.LogConfig;
import littlejlib.proj.ShellInit;
import littlejlib.proj.StdoutChannel;
import littlejlib.proj.shell.Clink;
import littlejlib.proj.shell.ShellIntegration;

/**
 * The shell adapter is the piece that actually changes your directory, because a child process
 * never can. These verbs put it in place and take it out again.
 */
public final class ShellCommands {

    private ShellCommands() { }

    static final java.io.PrintStream OUT = StdoutChannel.data();
    static final java.io.PrintStream ERR = LogConfig.err();

    @Command(name = "shell", description = "Install/remove the shell adapter that performs the cd",
             subcommands = { StatusCmd.class, InstallCmd.class, UninstallCmd.class, EmitCmd.class })
    public static class ShellCmd implements Runnable {
        @Override
        public void run() {
            new StatusCmd().run();
        }
    }

    @Command(name = "status", description = "Show which adapters are detected and installed")
    public static class StatusCmd implements Runnable {
        @Override
        public void run() {
            OUT.println("clink installed : " + (Clink.installed() ? Clink.exe() : "no"));
            if (Clink.installed()) {
                OUT.println("clink scripts   : " + Clink.reportedScriptDirs());
                OUT.println("chosen dir      : " + Clink.scriptDir());
            }
            OUT.println("auto-detected   : " + ShellIntegration.autoDetect());
            OUT.println();
            for (var kind : ShellIntegration.KINDS) {
                var target = ShellIntegration.targetFor(kind);
                OUT.printf("%-11s %-10s %s%n", kind,
                        ShellIntegration.isInstalled(kind) ? "INSTALLED" : "-",
                        target == null ? "(no location)" : target);
            }
        }
    }

    @Command(name = "install", description = "Install the adapter (default: auto-detect)")
    public static class InstallCmd implements Runnable {
        @Parameters(index = "0", arity = "0..1", description = "clink | cmd | powershell | bash")
        String kind;

        @Override
        public void run() {
            var target = kind == null ? ShellIntegration.autoDetect() : kind.trim().toLowerCase();
            if (ShellInit.resourceFor(target) == null) {
                ERR.println("unknown adapter: " + target + " (expected one of " + ShellIntegration.KINDS + ")");
                return;
            }
            try {
                var where = ShellIntegration.install(target);
                ERR.println("installed " + target + " adapter -> " + where);
                ERR.println(switch (target) {
                    case "clink" -> "Open a new cmd window; Clink loads it automatically.";
                    case "cmd" -> "Run it per session, or register it as AutoRun:\n"
                            + "  reg add \"HKCU\\Software\\Microsoft\\Command Processor\" /v AutoRun /d \""
                            + where + "\" /f\nIt defines `projcd`, not `proj` -- see the file header for why.";
                    case "powershell" -> "Open a new PowerShell session, or run: . $PROFILE";
                    default -> "Open a new shell, or run: source " + where;
                });
            } catch (Exception e) {
                ERR.println("install failed: " + e.getMessage());
            }
        }
    }

    @Command(name = "uninstall", description = "Remove the adapter")
    public static class UninstallCmd implements Runnable {
        @Parameters(index = "0", arity = "0..1", description = "clink | cmd | powershell | bash")
        String kind;

        @Override
        public void run() {
            var kinds = kind == null ? ShellIntegration.KINDS
                    : java.util.List.of(kind.trim().toLowerCase());
            for (var k : kinds) {
                try {
                    var where = ShellIntegration.uninstall(k);
                    if (where != null) ERR.println("removed " + k + " adapter from " + where);
                    else if (kind != null) ERR.println(k + " adapter was not installed");
                } catch (Exception e) {
                    ERR.println("could not remove " + k + ": " + e.getMessage());
                }
            }
        }
    }

    @Command(name = "emit", description = "Print the adapter to stdout instead of installing it")
    public static class EmitCmd implements Runnable {
        @Parameters(index = "0", arity = "0..1", description = "clink | cmd | powershell | bash")
        String kind;

        @Override
        public void run() {
            ShellInit.emit(kind == null ? ShellIntegration.autoDetect() : kind);
        }
    }
}
