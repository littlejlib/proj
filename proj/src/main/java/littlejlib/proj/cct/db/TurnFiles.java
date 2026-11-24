package littlejlib.proj.cct.db;

import littlejlib.proj.cct.model.TurnDoc;
import littlejlib.proj.store.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The turns of each conversation, one JSON line per turn in {@code <sessionId>.jsonl}. Only the
 * indexer writes them (under {@link Db#indexing}); a search reads them as they are.
 */
public final class TurnFiles {

    private final Path dir;

    TurnFiles(Path dir) {
        this.dir = dir;
    }

    Path fileOf(String sessionId) {
        return dir.resolve(sessionId + ".jsonl");
    }

    /** Every turn of one conversation, in seq order. */
    public List<TurnDoc> of(String sessionId) {
        var out = new ArrayList<TurnDoc>();
        read(fileOf(sessionId), line -> true, out::add);
        out.sort(Comparator.comparingInt(t -> t.seq() == null ? 0 : t.seq()));
        return out;
    }

    public void append(String sessionId, Collection<TurnDoc> turns) {
        if (turns.isEmpty()) return;
        var sb = new StringBuilder();
        for (var t : turns) sb.append(Json.GSON.toJson(t)).append('\n');
        try {
            Files.createDirectories(dir);
            Files.writeString(fileOf(sessionId), sb, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void drop(String sessionId) {
        try {
            Files.deleteIfExists(fileOf(sessionId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Removes the turns with these keys from one conversation. */
    public void dropKeys(String sessionId, Collection<String> turnKeys) {
        if (turnKeys.isEmpty()) return;
        var gone = new HashSet<>(turnKeys);
        var keep = new ArrayList<TurnDoc>();
        read(fileOf(sessionId), line -> true, t -> {
            if (!gone.contains(t.turnKey())) keep.add(t);
        });
        drop(sessionId);
        append(sessionId, keep);
    }

    /** Every session that has a turn file. */
    public List<String> sessionIds() {
        try (var s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".jsonl"))
                    .map(n -> n.substring(0, n.length() - ".jsonl".length())).toList();
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Every turn of every conversation whose raw JSON line passes {@code line}: a cheap check before
     * parsing, which is where a search spends its time.
     */
    public void scan(Predicate<String> line, Consumer<TurnDoc> each) {
        for (var id : sessionIds()) read(fileOf(id), line, each);
    }

    static void read(Path file, Predicate<String> line, Consumer<TurnDoc> each) {
        try (var r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (String s; (s = r.readLine()) != null; ) {
                if (s.isEmpty() || !line.test(s)) continue;
                try {
                    var t = Json.GSON.fromJson(s, TurnDoc.class);
                    if (t != null) each.accept(t);
                } catch (com.google.gson.JsonParseException e) {
                    // a line cut short by a crash mid-append; the next index of that session rewrites it
                }
            }
        } catch (NoSuchFileException e) {
            // no turns
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
