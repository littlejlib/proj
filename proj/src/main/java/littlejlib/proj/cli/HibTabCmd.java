package littlejlib.proj.cli;

import littlejlib.proj.hib.Hibernator;
import littlejlib.proj.hib.LiveSessions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

import static littlejlib.proj.cli.PrpCommands.ERR;

@Command(name = "tab", mixinStandardHelpOptions = true,
        description = "Hibernate other running conversations, by pid, name, name fragment or session id prefix")
public final class HibTabCmd implements Runnable {

    @Parameters(arity = "1..*", description = "Which conversations (see 'proj hib ls')")
    List<String> targets;

    @Option(names = "--anyway", description = "Hibernate even when a conversation is not on a PRP")
    boolean anyway;

    @Option(names = "--force", description = "Hibernate even when a conversation is busy mid-turn")
    boolean force;

    @Option(names = "--dry-run", description = "Say what would happen, change nothing")
    boolean dryRun;

    @Option(names = "--prp", description = "The PRP, when the detected one is wrong: a PRP file, or a number in its <cwd>/prp (one target only)")
    String prp;

    @Override
    public void run() {
        var self = LiveSessions.self();
        var worst = 0;
        for (var t : targets) {
            var hits = LiveSessions.find(t);
            if (hits.size() != 1) {
                ERR.println(hits.isEmpty() ? "no running conversation matches " + t
                        : t + " matches " + hits.size() + ": " + hits.stream().map(s -> s.label()).toList());
                worst = Math.max(worst, 4);
                continue;
            }
            var s = hits.get(0);
            var isSelf = self != null && self.pid().equals(s.pid());
            worst = Math.max(worst, HibCli.report(Hibernator.run(s, isSelf, anyway, force, dryRun, targets.size() == 1 ? prp : null)));
        }
        System.exit(worst);
    }
}
