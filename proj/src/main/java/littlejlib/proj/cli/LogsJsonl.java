package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.track.LogCopy;
import littlejlib.proj.track.Timeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The default {@code proj prp logs}: the session's transcript minus noise, one jsonl file per session
 * per PRP, plus a {@code log} line in the PRP's timeline that names the file and the session.
 */
final class LogsJsonl {

    private LogsJsonl() { }

    static LogCopy.Written export(Path prpDir, PrpEntry entry, Path transcript) throws IOException {
        var w = LogCopy.write(prpDir, entry.getIndex(), transcript);
        var name = w.file().getFileName().toString();
        var notes = Timeline.read(prpDir, entry.getIndex());
        var noted = notes.stream().anyMatch(n -> "log".equals(n.kind()) && w.sessionId().equals(n.session())
                && n.refs() != null && n.refs().contains(name));
        if (!noted) Timeline.add(prpDir, entry.getIndex(), TrackCli.uuid(entry), "log",
                "session exported as " + name, List.of(name), w.sessionId());
        return w;
    }

    static String report(LogCopy.Written w) {
        return (w.replaced() ? "replaced " : "wrote ") + w.kept() + " records (" + w.bytesOut() / 1024 + " KB of "
                + w.bytesIn() / 1024 + " KB; " + w.dropped() + " noise records left out) from session " + w.sessionId();
    }
}
