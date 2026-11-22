package littlejlib.proj.cli;

import littlejlib.proj.track.Now;
import littlejlib.proj.track.Session;
import littlejlib.proj.track.Timeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

/** Adds one line to a PRP's timeline. This is how an agent records status now, instead of editing NN-prp.status.md. */
@Command(name = "note", description = "Add a note to a PRP's timeline (NN-prp.status.jsonl): what was built, tested, decided, handed over")
public class PrpNoteCmd implements Runnable {

    @Parameters(index = "0", description = "PRP index")
    String index;

    @Parameters(index = "1..*", arity = "1..*", description = "The note, at most " + Timeline.MAX_TEXT + " characters")
    List<String> text;

    @Option(names = {"-k", "--kind"}, description = "note (default), decision, scope, question, handover, built, tested, "
            + "confirmed (only after the user said so), stalled, closed, start, resume, log")
    String kind;

    @Option(names = "--ref", description = "A file, commit, item id or PRP this note points at; repeatable")
    List<String> refs;

    @Option(names = "--session", description = "Session id. Defaults to CLAUDE_CODE_SESSION_ID")
    String session;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var prpDir = PrpCommands.prpDir(dir);
            var entry = PrpCommands.resolve(prpDir, index);
            var n = Timeline.add(prpDir, entry.getIndex(), TrackCli.uuid(entry), kind, String.join(" ", text), refs,
                    Session.current(session));
            PrpCommands.OUT.println(entry.getIndex() + "  " + Now.shown(n.at()) + "  " + n.kind() + "  " + n.text());
        });
    }
}
