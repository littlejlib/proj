package littlejlib.proj.cct.export;

import module java.base;

import littlejlib.proj.cct.index.*;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

/**
 * One session read straight off disk, with no database in the way.
 * <p>
 * The index would answer this faster, but it cannot answer it for the session you are IN: that file
 * is still being appended to, and the whole point of exporting a log is to capture the conversation
 * that just happened. So this streams the file itself - same reader, same never-materialise-base64
 * rule, same prompt de-duplication - and works whether or not anything has been indexed and whether
 * or not a daemon is holding the index.
 */
public final class SessionRead {

    private SessionRead() {}

    public record Read(SessionDoc session, List<TurnDoc> turns) { }

    public static Read of(Path jsonl, int widestLayer) {
        var sessionId = sessionIdOf(jsonl);
        var doc = new SessionDoc().sessionId(sessionId).filePath(jsonl.toString()).fileBytes(size(jsonl));
        var acc = new SessionAccumulator(doc);
        var opts = IndexOptions.defaults().includeAssistant(widestLayer >= 2).includeThinking(false);
        var kept = new ArrayList<TurnDoc>();
        var human = new HumanTurns(List.of(), kept::add);
        var seq = 0;
        try (var cur = new LineCursor(jsonl, 0)) {
            while (cur.next()) {
                var rec = RecordReader.read(cur.buffer(), cur.length());
                if (rec != null) {
                    acc.apply(rec);
                    for (var t : TurnFactory.of(rec, sessionId, seq, opts)) human.accept(t);
                }
                seq++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + jsonl, e);
        }
        var resolved = human.resolve();
        kept.addAll(resolved.keep());
        kept.sort(Comparator.comparingInt(t -> t.seq() == null ? 0 : t.seq()));
        for (var t : kept) acc.countTurn(t);
        try {
            acc.finish(0, Files.size(jsonl), Files.getLastModifiedTime(jsonl).toMillis(), seq);
        } catch (IOException e) {
            acc.finish(0, 0, 0, seq);
        }
        doc.humanTurns(resolved.total());
        return new Read(acc.doc(), kept);
    }

    static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    public static String sessionIdOf(Path jsonl) {
        var name = jsonl.getFileName().toString();
        return name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name;
    }

    /**
     * The session that ran in this directory, most recent first.
     * <p>
     * Found through the encoded folder name, which is the one direction of that mapping that is
     * safe: encoding a path is deterministic, DECODING one is not - every non-alphanumeric character
     * becomes the same dash, so a folder name cannot be turned back into a path. Nothing here tries.
     */
    public static List<Path> sessionsOfDirectory(Path cwd) {
        var dir = StorePaths.projectsDir().resolve(PathKey.encode(cwd.toAbsolutePath().toString()));
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                .sorted(Comparator.comparingLong(SessionRead::modified).reversed())
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** A session by id or by any unambiguous prefix, across every project folder. */
    public static List<Path> byId(String idOrPrefix) {
        var out = new ArrayList<Path>();
        if (idOrPrefix == null || idOrPrefix.isBlank()) return out;
        var want = idOrPrefix.toLowerCase(Locale.ROOT);
        for (var f : StoreScanner.scan()) {
            if (Boolean.TRUE.equals(f.subagent())) continue;
            if (f.sessionId() != null && f.sessionId().toLowerCase(Locale.ROOT).startsWith(want))
                out.add(Path.of(f.path()));
        }
        return out;
    }

    static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
