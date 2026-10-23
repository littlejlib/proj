package littlejlib.proj.hib;

import module java.base;

import com.google.gson.Gson;
import littlejlib.proj.util.AppPaths;

/**
 * {@code ~/littlejlib/proj/hibernated.jsonl}: append-only, one full record per event, the last line
 * for a session id is its state. A file rather than the index so it works with or without the daemon
 * and survives an index rebuild.
 */
public final class HibStore {

    private HibStore() { }

    static final Gson GSON = new Gson();

    public static Path file() {
        return AppPaths.home().resolve("hibernated.jsonl");
    }

    /** Logs of conversations that had no PRP to put them in. */
    public static Path looseLogs() {
        return AppPaths.home().resolve("hibernated");
    }

    public static synchronized void append(HibRecord r) {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(r) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file(), e);
        }
    }

    /** Current state per session, most recently hibernated first. */
    public static List<HibRecord> all() {
        var latest = new LinkedHashMap<String, HibRecord>();
        if (!Files.exists(file())) return List.of();
        try {
            for (var line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    var r = GSON.fromJson(line, HibRecord.class);
                    if (r != null && r.sessionId() != null) latest.put(r.sessionId(), r);
                } catch (RuntimeException ignored) { }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file(), e);
        }
        return latest.values().stream()
                .sorted(Comparator.comparingLong((HibRecord r) -> r.hibernatedAt() == null ? 0 : r.hibernatedAt()).reversed())
                .toList();
    }

    public static List<HibRecord> find(String target, boolean includeRestored) {
        var t = target.strip().toLowerCase(Locale.ROOT);
        var pool = all().stream().filter(r -> includeRestored || r.hibernated()).toList();
        for (var test : List.<java.util.function.Predicate<HibRecord>>of(
                r -> t.equals(lower(r.name())),
                r -> lower(r.sessionId()).startsWith(t),
                r -> lower(r.name()).contains(t))) {
            var hits = pool.stream().filter(test).toList();
            if (!hits.isEmpty()) return hits;
        }
        return List.of();
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
