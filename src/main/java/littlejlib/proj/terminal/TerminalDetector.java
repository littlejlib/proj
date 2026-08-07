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
            var exe = ToolLocator.locate(spec.executables);
            if (exe == null) continue;
            found.add(new TerminalLauncher(entry.getKey(),
                    quote(exe) + " " + spec.openArgs,
                    spec.runArgs == null ? null : quote(exe) + " " + spec.runArgs));
        }
        return found;
    }

    static String quote(Path exe) {
        return "\"" + exe.toAbsolutePath() + "\"";
    }

    record Spec(List<String> executables, String openArgs, String runArgs) { }

    static Map<String, Spec> candidates() {
        var out = new LinkedHashMap<String, Spec>();

        if (WINDOWS) {
            // TABBY, measured rather than read off its help text. `Tabby.exe open <dir>` works: a
            // new tab appears with a shell in that directory. `Tabby.exe run <command>` does NOT -
            // on 1.0.234 it opens a tab and silently ignores the command, and the same is true of
            // its documented /k alias and of every argument shape tried. So the run template is
            // left EMPTY, which routes the agent to a terminal that can actually run something,
            // rather than filled in with a plausible-looking command that quietly does nothing.
            // If a later Tabby fixes this, the run template is editable in Settings.
            out.put("Tabby", new Spec(List.of("Tabby.exe"), "open \"%PATH%\"", null));

            // Not installed on the machine this was written on, so these two are unverified.
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
