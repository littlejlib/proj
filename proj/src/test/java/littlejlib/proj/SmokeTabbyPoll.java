package littlejlib.proj;

import littlejlib.proj.terminal.TabbyMcp;
import littlejlib.proj.terminal.TerminalRunner;
import littlejlib.proj.util.Cmd;

import java.nio.file.Path;
import java.util.HashSet;

/** How long Tabby takes to register a tab opened from the command line, measured rather than guessed. */
public class SmokeTabbyPoll {

    public static void main(String[] args) throws Exception {
        var dir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        var settings = new SettingsManager().loadSettings();
        var tabby = TerminalRunner.resolve(settings);
        var exe = Cmd.parse(tabby.getOpenCommand())[0];

        var before = new HashSet<>(TabbyMcp.sessionIds());
        System.out.println("port " + TabbyMcp.port() + "  before=" + before.size() + "  exe=" + exe);

        var started = System.currentTimeMillis();
        System.out.println("spawn ok=" + Cmd.run(new String[]{exe, dir.toString()}, null));

        for (var i = 0; i < 200; i++) {
            var ids = TabbyMcp.sessionIds();
            var fresh = ids.stream().filter(id -> !before.contains(id)).findFirst().orElse(null);
            if (fresh != null) {
                System.out.println("new session after " + (System.currentTimeMillis() - started)
                        + "ms  " + fresh + "   (count " + ids.size() + ")");
                for (var j = 0; j < 40; j++) {
                    var buffer = TabbyMcp.buffer(fresh, 4);
                    if (buffer != null && buffer.stripTrailing().endsWith(">")) {
                        System.out.println("prompt ready after "
                                + (System.currentTimeMillis() - started) + "ms");
                        System.out.println("sent=" + TabbyMcp.sendInput(fresh, "echo POLL_OK\r"));
                        return;
                    }
                    Thread.sleep(150);
                }
                System.out.println("prompt never matched; last buffer tail: "
                        + String.valueOf(TabbyMcp.buffer(fresh, 3)).replace("\r", "\\r").replace("\n", "\\n"));
                return;
            }
            Thread.sleep(150);
        }
        System.out.println("no new session in 30s");
    }
}
