package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpSessions;
import littlejlib.proj.prp.TabName;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

/**
 * The project's key: the {@code billing} that makes PRP 38 of that project {@code billing-38}.
 * <p>
 * It behaves like a JIRA project key and is meant to: short, stable, said out loud, and unique
 * enough to identify one piece of work among every project on the machine. The terminal tab name is
 * that ticket with the task appended, so setting this also fixes what every future tab is called.
 */
@Command(name = "key", description = "Show or set this project's short name - the billing of billing-38")
public class PrpKeyCmd implements Runnable {

    @Parameters(arity = "0..1", description = "The key to set. With none, prints the current one")
    String key;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        var project = PrpCommands.projectDir(dir);
        if (key == null || key.isBlank()) {
            var current = PrpSessions.key(project);
            PrpCommands.OUT.println(current == null
                    ? TabName.shortenProject(project) + "   (guessed - none set)" : current);
            return;
        }
        var clean = TabName.cleanKey(key);
        if (clean.isEmpty()) {
            PrpCommands.ERR.println("a key needs at least one letter or digit");
            System.exit(2);
            return;
        }
        PrpSessions.setKey(project, clean);
        PrpCommands.OUT.println(clean + "   " + project);
    }
}
