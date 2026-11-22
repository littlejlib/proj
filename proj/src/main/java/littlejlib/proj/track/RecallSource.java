package littlejlib.proj.track;

import littlejlib.proj.cct.export.SessionRead;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpLogs;
import littlejlib.proj.prp.PrpScanner;
import littlejlib.proj.prp.PrpSessions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The sessions that worked on a PRP, newest first, each with a readable transcript: claude-code's own
 * file when it is still there, else the jsonl log copy in the prp folder. Ids come from the PRP's
 * timeline, proj's launch records and the log copies.
 */
public final class RecallSource {

    private RecallSource() { }

    public record Source(String sessionId, Path file, long modified) { }

    public static List<Source> of(Path prpDir, PrpEntry e) throws IOException {
        var copies = new LinkedHashMap<String, Path>();
        for (var log : LogCopy.logsOf(prpDir, e.getIndex())) {
            var s = LogCopy.sessionOf(log);
            if (s != null) copies.put(s, log);
        }
        var ids = new ArrayList<String>(Timeline.sessions(Timeline.read(prpDir, e.getIndex())));
        try {
            for (var row : PrpSessions.of(e.getMainFile())) ids.add(row.sessionId());
        } catch (RuntimeException ignored) {
            // the launch records are a help, not a need: without the database the other two still answer
        }
        ids.addAll(copies.keySet());
        for (var log : PrpLogs.sessionsOf(prpDir, e.getIndex())) {
            var s = headerSession(log.file());
            if (s != null) ids.add(s);
        }
        var out = new ArrayList<Source>();
        for (var id : ids.stream().distinct().toList()) {
            var file = transcript(id);
            if (file == null) file = copies.get(id);
            if (file != null) out.add(new Source(id, file, PrpScanner.mtime(file)));
        }
        out.sort(Comparator.comparingLong(Source::modified).reversed());
        return out;
    }

    /** The newest session that is not {@code current}, or the one asked for. */
    public static Source pick(List<Source> all, String current, String asked) {
        if (asked != null && !asked.isBlank())
            return all.stream().filter(s -> s.sessionId().startsWith(asked.strip())).findFirst().orElse(null);
        return all.stream().filter(s -> !s.sessionId().equals(current)).findFirst().orElse(null);
    }

    static final Pattern HEADER_SESSION = Pattern.compile("^\\s*-?\\s*session\\s+`?([0-9a-f]{8}-[0-9a-f-]{27})`?");

    /** The session id in the header of a markdown log written by an earlier proj ({@code - session `id`}). */
    static String headerSession(Path log) {
        if (!log.getFileName().toString().endsWith(".md")) return null;
        try (var lines = Files.lines(log, StandardCharsets.UTF_8)) {
            return lines.limit(40).map(HEADER_SESSION::matcher).filter(Matcher::find).map(m -> m.group(1)).findFirst().orElse(null);
        } catch (IOException | RuntimeException x) {
            return null;
        }
    }

    public static Path transcript(String id) {
        var found = SessionRead.byId(id).stream().filter(p -> p.getFileName().toString().equals(id + ".jsonl")).toList();
        return found.stream().max(Comparator.comparingLong(p -> size(p))).orElse(null);
    }

    static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }
}
