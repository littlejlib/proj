package littlejlib.proj;

import littlejlib.proj.terminal.TerminalDetector;
import littlejlib.proj.terminal.TerminalRunner;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exercises terminal detection and the run path with a harmless command, so the generated script
 * and the terminal's own run verb are proven without starting a real coding agent.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SmokeTerminal" -Dexec.classpathScope=test
 */
public class SmokeTerminal {

    public static void main(String[] args) throws Exception {
        var settings = new SettingsManager().loadSettings();

        System.out.println("detected terminals:");
        for (var t : TerminalDetector.detect()) {
            System.out.println("   " + t.getName());
            System.out.println("      open " + t.getOpenCommand());
            System.out.println("      run  " + t.getRunCommand());
        }

        var chosen = TerminalRunner.resolve(settings);
        System.out.println("\nresolved: " + chosen.getName() + "   canRun=" + chosen.canRun());

        // The script is the part worth checking: it is what makes the working directory correct for
        // a terminal whose run verb accepts no directory of its own.
        var target = Path.of(System.getProperty("user.dir"));
        var script = writeProbe(target);
        System.out.println("\ngenerated script at " + script);
        System.out.println("----");
        System.out.println(Files.readString(script));
        System.out.println("----");

        var launch = Boolean.parseBoolean(System.getProperty("launch", "false"));
        if (!launch) {
            System.out.println("\nnot launching; pass -Dlaunch=true to actually open a terminal tab");
            return;
        }

        // A marker file, not an echo. Cmd.run returning true only means a process started without
        // throwing; it says nothing about whether the terminal understood the arguments or ran
        // anything. The marker is written BY the generated script, in the directory the script was
        // told to change into, so its existence proves both halves at once.
        var probeDir = Path.of(System.getProperty("java.io.tmpdir"), "proj-terminal-smoke");
        Files.createDirectories(probeDir);
        var marker = probeDir.resolve("marker.txt");
        Files.deleteIfExists(marker);

        var ok = TerminalRunner.run(probeDir, "cd > marker.txt", "smoke", settings);
        System.out.println("process started = " + ok);

        for (var i = 0; i < 60 && !Files.exists(marker); i++) Thread.sleep(250);

        if (Files.exists(marker)) {
            System.out.println("MARKER WRITTEN. The script ran, in: " + Files.readString(marker).trim());
        } else {
            System.out.println("NO MARKER after 15s - the terminal did not run the script.");
        }
    }

    /** Reaches the package-private writer through the same path TerminalRunner.run uses. */
    static Path writeProbe(Path target) throws Exception {
        var m = TerminalRunner.class.getDeclaredMethod("writeScript", Path.class, String.class, String.class);
        m.setAccessible(true);
        return (Path) m.invoke(null, target, "echo proj terminal smoke test && cd", "smoke");
    }
}
