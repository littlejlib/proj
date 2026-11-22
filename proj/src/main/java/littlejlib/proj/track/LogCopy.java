package littlejlib.proj.track;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import littlejlib.proj.prp.PrpFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A PRP's log: the session's own transcript records, minus the noise (request blobs, file history,
 * mode and title records rewritten every turn, attachments other than queued prompts). One file per
 * session per PRP: exporting the same session again overwrites its file instead of adding a copy.
 */
public final class LogCopy {

    private LogCopy() { }

    static final Set<String> KEEP = Set.of("user", "assistant", "system", "summary");

    public record Written(Path file, String sessionId, boolean replaced, int kept, int dropped, long bytesIn, long bytesOut) { }

    public static Written write(Path prpDir, String prpIndex, Path transcript) throws IOException {
        var kept = new ArrayList<String>();
        var dropped = 0;
        var session = (String) null;
        for (var line : Files.readAllLines(transcript, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            var o = parse(line);
            if (o == null || !keep(o)) {
                dropped++;
                continue;
            }
            if (session == null) session = Conversation.str(o, "sessionId");
            kept.add(line);
        }
        if (session == null) session = transcript.getFileName().toString().replaceAll("\\.jsonl$", "");
        var existing = fileOf(prpDir, prpIndex, session);
        var target = existing != null ? existing : prpDir.resolve(nextName(prpDir, prpIndex));
        var tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, String.join("\n", kept) + "\n", StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return new Written(target, session, existing != null, kept.size(), dropped, Files.size(transcript), Files.size(target));
    }

    static boolean keep(JsonObject o) {
        var type = Conversation.str(o, "type");
        if ("attachment".equals(type)) {
            var a = o.get("attachment");
            return a != null && a.isJsonObject() && "queued_command".equals(Conversation.str(a.getAsJsonObject(), "type"));
        }
        return type != null && KEEP.contains(type);
    }

    static JsonObject parse(String line) {
        try {
            return JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The jsonl logs of one PRP, oldest serial first. */
    public static List<Path> logsOf(Path prpDir, String prpIndex) throws IOException {
        if (!Files.isDirectory(prpDir)) return List.of();
        try (var files = Files.list(prpDir)) {
            return files.filter(f -> {
                var log = PrpFiles.parseLog(f.getFileName().toString());
                return log != null && log[0].equalsIgnoreCase(prpIndex) && log[2].equals("jsonl");
            }).sorted().toList();
        }
    }

    /** The session a jsonl log holds, from its first record that names one. */
    public static String sessionOf(Path log) {
        try (var lines = Files.lines(log, StandardCharsets.UTF_8)) {
            return lines.limit(50).map(LogCopy::parse).filter(o -> o != null && Conversation.str(o, "sessionId") != null)
                    .map(o -> Conversation.str(o, "sessionId")).findFirst().orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static Path fileOf(Path prpDir, String prpIndex, String session) throws IOException {
        for (var log : logsOf(prpDir, prpIndex)) if (session.equals(sessionOf(log))) return log;
        return null;
    }

    /** After the highest serial of ANY log of this PRP (txt, md or jsonl), so serials never repeat. */
    public static String nextName(Path prpDir, String prpIndex) throws IOException {
        var max = 0;
        try (var files = Files.list(prpDir)) {
            for (var f : files.toList()) {
                var log = PrpFiles.parseLog(f.getFileName().toString());
                if (log != null && log[0].equalsIgnoreCase(prpIndex)) max = Math.max(max, Integer.parseInt(log[1]));
            }
        }
        return PrpFiles.log(prpIndex, max + 1, "jsonl");
    }
}
