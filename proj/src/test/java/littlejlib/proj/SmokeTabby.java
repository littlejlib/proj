package littlejlib.proj;

import littlejlib.proj.terminal.TabbyMcp;
import littlejlib.proj.terminal.TerminalRunner;

import java.nio.file.Path;

/**
 * Opens a real tab and runs a harmless command in it, saying which of the three things happened.
 * <p>
 * There is no way to unit-test this: it is one process asking a running GUI to do something, and
 * every failure mode it exists to catch - a dead command line, an absent plugin, a shell that was
 * not ready for input - is invisible until a tab actually opens.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.SmokeTabby" -Dexec.classpathScope=test -Dexec.args="[dir]"
 */
public class SmokeTabby {

    public static void main(String[] args) {
        var dir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        var settings = new SettingsManager().loadSettings();
        var terminal = TerminalRunner.resolve(settings);

        System.out.println("terminal   " + terminal.getName());
        System.out.println("open       " + terminal.getOpenCommand());
        System.out.println("run        " + terminal.getRunCommand());
        System.out.println("mcp port   " + TabbyMcp.port());
        System.out.println("sessions   " + TabbyMcp.sessionIds().size());
        System.out.println("directory  " + dir);

        var started = System.currentTimeMillis();
        var outcome = TerminalRunner.outcomeOf(terminal, dir,
                "echo proj launch smoke ok && ver", "smoke", settings, "proj-smoke");
        System.out.println("outcome    " + outcome + "   (" + (System.currentTimeMillis() - started)
                + "ms)  " + outcome.note());
    }
}
