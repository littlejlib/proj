package littlejlib.proj;

import littlejlib.proj.db.DB;
import littlejlib.proj.prp.PrpSessions;

import java.nio.file.Path;

/**
 * The recorded-session store as a CLI process sees it: the database is held by the daemon, so every
 * answer here has to arrive over the daemon route or not at all.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.SmokeSessions" -Dexec.classpathScope=test -Dexec.args="<prpFile> [sessionId] [tabName]"
 */
public class SmokeSessions {

    public static void main(String[] args) {
        var prpFile = Path.of(args[0]).toAbsolutePath().normalize();
        var project = littlejlib.proj.prp.AgentLauncher.projectRootOf(prpFile);

        System.out.println("db open here  " + DB.isOpen() + "   (false means the daemon must answer)");
        try {
            var raw = littlejlib.proj.daemon.Client.send(
                    java.util.List.of("query", "prp-session", "list", prpFile.toString()),
                    false, m -> { });
            System.out.println("daemon exit   " + raw.exitCode);
            System.out.println("daemon out    [" + raw.stdout + "]");
            System.out.println("daemon err    [" + raw.stderr + "]");
        } catch (Exception e) {
            System.out.println("daemon call failed: " + e);
        }
        System.out.println("tab name      " + PrpSessions.tabName(prpFile));
        System.out.println("recorded      " + PrpSessions.of(prpFile).size() + " session(s)");

        if (args.length > 1) {
            var sessionId = args[1];
            var tabName = args.length > 2 ? args[2] : PrpSessions.tabName(prpFile);
            PrpSessions.record(sessionId, prpFile,
                    littlejlib.proj.prp.PrpPrompt.indexOf(prpFile), project, tabName);
            System.out.println("wrote         " + sessionId + "  as  " + tabName);

            for (var s : PrpSessions.of(prpFile))
                System.out.println("  " + s.sessionId() + "  " + s.tabName()
                        + "  " + littlejlib.proj.cct.export.Stamp.of(s.started()));
            System.out.println("tab name now  " + PrpSessions.tabName(prpFile));
        }
    }
}
