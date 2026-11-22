package littlejlib.proj.cli;

import littlejlib.proj.track.Session;
import littlejlib.proj.track.Workplan;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

/** Changes an item by appending its new whole state; the old line stays as history. */
@Command(name = "set", description = "Change a work item: status, type, title, PRP, deps ...")
public class PlanSetCmd implements Runnable {

    @Parameters(index = "0", description = "Item id")
    long id;

    @Option(names = {"-s", "--status"}, description = "open, done, dropped, hold, blocked")
    String status;

    @Option(names = {"-t", "--type"}, description = "todo, discuss, confirm")
    String type;

    @Option(names = "--title", description = "New title")
    String title;

    @Option(names = {"-p", "--prp"}, description = "New owning PRP (number or uuid); empty clears it")
    String prp;

    @Option(names = "--related", description = "Replace the related PRPs")
    List<String> related;

    @Option(names = "--after", description = "Replace what this item waits for: ids of this project, or Project#id of another")
    List<String> after;

    @Option(names = "--blocks", description = "Add: these items wait for this one (ids, or Project#id of another project)")
    List<String> blocks;

    @Option(names = "--detail", description = "Replace the detail; empty clears it")
    String detail;

    @Option(names = "--ref", description = "Replace the refs; repeatable")
    List<String> refs;

    @Option(names = "--owner", description = "Who acts on it")
    String owner;

    @Option(names = "--source", description = "Who asked and when")
    String source;

    @Option(names = "--tag", description = "Importance: critical, infra, extra; empty clears it")
    String tag;

    @Option(names = "--uses", description = "Replace the shared resources (never run together with another item using one), e.g. git:billing-core, account:free*2; empty clears them")
    List<String> uses;

    @Option(names = "--force", description = "Set doing even though a live item holds a resource this one uses")
    boolean force;

    @Option(names = "--session", description = "Session id. Defaults to CLAUDE_CODE_SESSION_ID")
    String session;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var next = Workplan.set(prpDir, id, i -> PlanCli.apply(i, prpDir, type, status, title, detail, prp,
                    related, after, refs, owner, source, tag, uses), Session.current(session), force ? null : PlanCli::refuseHeld);
            PlanCli.blocks(prpDir, id, blocks, Session.current(session));
            PrpCommands.OUT.println(next.id() + "  " + next.type() + "  " + next.status() + "  " + next.title());
        });
    }
}
