package littlejlib.proj.cli;

import littlejlib.proj.track.Item;
import littlejlib.proj.track.Session;
import littlejlib.proj.track.Workplan;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "add", description = "Add a work item; prints its id")
public class PlanAddCmd implements Runnable {

    @Parameters(arity = "1..*", description = "The title, at most " + Workplan.MAX_TITLE + " characters")
    List<String> title;

    @Option(names = {"-t", "--type"}, defaultValue = "todo", description = "todo (default), discuss, confirm")
    String type;

    @Option(names = {"-p", "--prp"}, description = "The owning PRP: a number in this project or a uuid")
    String prp;

    @Option(names = "--related", description = "Related PRPs (numbers or uuids); repeatable or comma separated")
    List<String> related;

    @Option(names = "--after", description = "What this one waits for: ids of this project, or Project#id of another (checked; stored with the project's uuid); repeatable or comma separated")
    List<String> after;

    @Option(names = "--blocks", description = "Items that wait for this one: ids, or Project#id of another project (written into that project's plan)")
    List<String> blocks;

    @Option(names = "--detail", description = "One to three sentences, at most " + Workplan.MAX_DETAIL + " characters")
    String detail;

    @Option(names = "--ref", description = "A file, commit or url; repeatable")
    List<String> refs;

    @Option(names = "--owner", description = "Who acts on it: user, agent, a PRP")
    String owner;

    @Option(names = "--source", description = "Who asked for it and when, e.g. \"user 2026-11-21 05:10 EET\"")
    String source;

    @Option(names = "--tag", description = "Importance: critical, infra (default when not given), extra")
    String tag;

    @Option(names = "--uses", description = "Shared resources: never run together with another item using one, e.g. git:billing-core, account:free*2; repeatable or comma separated")
    List<String> uses;

    @Option(names = "--session", description = "Session id. Defaults to CLAUDE_CODE_SESSION_ID")
    String session;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var draft = new Item().session(Session.current(session));
            PlanCli.apply(draft, prpDir, type, null, String.join(" ", title), detail, prp, related, after, refs, owner, source, tag, uses);
            var added = Workplan.add(prpDir, draft);
            PlanCli.blocks(prpDir, added.id(), blocks, Session.current(session));
            PrpCommands.OUT.println(added.id() + "  " + added.type() + "  " + added.title());
        });
    }
}
