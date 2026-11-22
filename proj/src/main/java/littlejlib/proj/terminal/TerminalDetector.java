package littlejlib.proj.terminal;

import littlejlib.proj.TerminalLauncher;
import littlejlib.proj.util.ToolLocator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The terminals actually installed on this machine, best first.
 * <p>
 * Ordered by preference rather than alphabetically: a user who installed Tabby did so in order to
 * stop using cmd, and offering cmd first would be offering the thing they replaced. The OS default
 * is last precisely because it is the fallback, not the choice.
 * <p>
 * Anything not detected here can be registered by hand in the settings window - the list below is a
 * convenience, not the boundary of what is supported.
 */
public final class TerminalDetector {

    private TerminalDetector() { }

    static final String OS = System.getProperty("os.name", "").toLowerCase();
    static final boolean WINDOWS = OS.contains("win"), MAC = OS.contains("mac");

    static volatile List<TerminalLauncher> cached;

    public static List<TerminalLauncher> detect() {
        var local = cached;
        if (local == null) {
            local = scan();
            cached = local;
        }
        return local;
    }

    public static void forget() {
        cached = null;
        ToolLocator.forget();
    }

    static List<TerminalLauncher> scan() {
        var found = new ArrayList<TerminalLauncher>();
        for (var entry : candidates().entrySet()) {
            var spec = entry.getValue();
            if (MAC) {
                // an app bundle, started by name with `open -a`: its commands are complete as written
                if (macApp(spec.executables.get(0)) != null)
                    found.add(new TerminalLauncher(entry.getKey(), spec.openArgs, spec.runArgs));
                continue;
            }
            var exe = ToolLocator.locate(spec.executables);
            if (exe == null) continue;
            found.add(new TerminalLauncher(entry.getKey(),
                    quote(exe) + " " + spec.openArgs,
                    spec.runArgs == null ? null : quote(exe) + " " + spec.runArgs));
        }
        return found;
    }

    /** The app bundle in the places macOS keeps apps: Terminal.app is a system app, under Utilities. */
    static Path macApp(String bundle) {
        var home = Path.of(System.getProperty("user.home", ""));
        for (var dir : List.of(Path.of("/Applications"), home.resolve("Applications"),
                Path.of("/System/Applications/Utilities"), Path.of("/System/Applications"))) {
            var app = dir.resolve(bundle);
            if (java.nio.file.Files.isDirectory(app)) return app;
        }
        return null;
    }

    static String quote(Path exe) {
        return "\"" + exe.toAbsolutePath() + "\"";
    }

    record Spec(List<String> executables, String openArgs, String runArgs) { }

    static Map<String, Spec> candidates() {
        var out = new LinkedHashMap<String, Spec>();

        if (WINDOWS) {
            // TABBY TAKES A BARE PATH AND NOTHING ELSE. Every command verb - open, run, profile,
            // paste - dies in its own argument parser before reaching a handler, silently, opening
            // nothing (cliui `stripAnsi is not a function`, measured in prp/32-prp.01.findings.md).
            // A bare path is handled below that parser and works. Running a command in the tab is
            // then TabbyLaunch's job, over the MCP plugin, because no command line can do it.
            out.put("Tabby", new Spec(List.of("Tabby.exe"),
                    "\"%PATH%\"",
                    "\"%PATH%\""));

            out.put("Windows Terminal", new Spec(List.of("wt.exe"),
                    "-d \"%PATH%\"",
                    "-d \"%PATH%\" cmd /k \"%SCRIPT%\""));
            out.put("WezTerm", new Spec(List.of("wezterm-gui.exe", "wezterm.exe"),
                    "start --cwd \"%PATH%\"",
                    "start --cwd \"%PATH%\" -- cmd /k \"%SCRIPT%\""));

            out.put("Git Bash", new Spec(List.of("git-bash.exe"), "--cd=\"%PATH%\"", null));
            out.put("PowerShell", new Spec(List.of("pwsh.exe", "powershell.exe"),
                    "-NoExit -Command Set-Location -LiteralPath '%PATH%'",
                    "-NoExit -Command & '%SCRIPT%'"));

            // No `start` wrapper. ProcessBuilder already spawns a detached process and cmd.exe
            // creates its own console, so `start` added nothing but a layer of quoting that broke
            // the command - verified: `cmd /k <script>` runs the script, `cmd /c start "" cmd /k
            // <script>` does not.
            out.put("Command Prompt", new Spec(List.of("cmd.exe"),
                    "/k cd /d \"%PATH%\"",
                    "/k \"%SCRIPT%\""));
            return out;
        }

        if (MAC) {
            out.put("iTerm2", new Spec(List.of("iTerm.app"), "open -a iTerm \"%PATH%\"", "open -a iTerm \"%SCRIPT%\""));
            out.put("Terminal", new Spec(List.of("Terminal.app"),
                    "open -a Terminal \"%PATH%\"", "open -a Terminal \"%SCRIPT%\""));
            return out;
        }

        out.put("GNOME Terminal", new Spec(List.of("gnome-terminal"),
                "--working-directory=\"%PATH%\"",
                "--working-directory=\"%PATH%\" -- bash -c \"%SCRIPT%; exec bash\""));
        out.put("Konsole", new Spec(List.of("konsole"),
                "--workdir \"%PATH%\"",
                "--workdir \"%PATH%\" -e bash -c \"%SCRIPT%; exec bash\""));
        out.put("xterm", new Spec(List.of("xterm"), "-e bash", "-e bash -c \"%SCRIPT%; exec bash\""));
        return out;
    }

    static final java.util.Set<String> DEAD_TABBY_VERBS =
            java.util.Set.of("open", "run", "profile", "paste", "recent", "quickconnect");

    /**
     * A settings file written before the Tabby verbs were known to be dead carries them verbatim,
     * having never been edited by anyone, and every launch through it does nothing at all. So such
     * an entry reads as unconfigured and is rebuilt around the same executable.
     */
    public static TerminalLauncher corrected(TerminalLauncher t) {
        if (!TabbyLaunch.handles(t) || !(deadVerb(t.getOpenCommand()) || deadVerb(t.getRunCommand())))
            return t;
        var exe = TabbyLaunch.exeOf(t);
        if (exe == null) return t;
        var bare = "\"" + exe + "\" \"%PATH%\"";
        return new TerminalLauncher(t.getName(), bare, bare);
    }

    static boolean deadVerb(String template) {
        if (template == null || template.isBlank()) return false;
        var argv = littlejlib.proj.util.Cmd.parse(template);
        return argv.length > 1 && DEAD_TABBY_VERBS.contains(argv[1].toLowerCase());
    }

    /**
     * What to use when nothing is configured and nothing was detected. Never null, because "no
     * terminal" is not an outcome a user can act on.
     */
    public static TerminalLauncher osFallback() {
        if (WINDOWS) {
            return new TerminalLauncher("Command Prompt",
                    "cmd.exe /k cd /d \"%PATH%\"",
                    "cmd.exe /k \"%SCRIPT%\"");
        }
        if (MAC) {
            return new TerminalLauncher("Terminal",
                    "open -a Terminal \"%PATH%\"", "open -a Terminal \"%SCRIPT%\"");
        }
        return new TerminalLauncher("x-terminal-emulator",
                "x-terminal-emulator --working-directory=\"%PATH%\"",
                "x-terminal-emulator -e bash -c \"%SCRIPT%; exec bash\"");
    }
}
