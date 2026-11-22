package littlejlib.proj.central;

import com.arcadedb.database.Database;
import littlejlib.proj.db.DB;
import littlejlib.proj.model.CentralArtifact;
import littlejlib.proj.model.CentralStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static littlejlib.proj.model.CentralArtifact_A.$coords;
import static datapotter.arcadedbhelper.Query.query;

/**
 * Answers "is this on Maven Central" lazily, off the UI thread, and remembers the answer.
 * <p>
 * Nothing here touches the database or the network while a cell is being rendered:
 * {@link #warmFromCache()} pulls every known answer into memory once, and {@link #request} is what
 * a visible row asks for. A published artifact is immutable on Central so its answer is kept
 * forever; a negative is retried after {@link #NEGATIVE_TTL}.
 */
public class CentralChecker {

    public static final Duration NEGATIVE_TTL = Duration.ofDays(7);
    static final String BASE = "https://repo1.maven.org/maven2/";

    final Map<String, CentralStatus> memory = new ConcurrentHashMap<>();
    final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        var t = new Thread(r, "CentralChecker");
        t.setDaemon(true);
        return t;
    });

    /** Loads every previously determined answer into memory. Call once, off the UI thread. */
    public void warmFromCache() {
        try {
            for (var a : query(db(), CentralArtifact.TYPEDEF).list()) {
                if (a.isFresh(NEGATIVE_TTL.toMillis())) {
                    memory.put(a.coords(), a.published() ? CentralStatus.PUBLISHED : CentralStatus.ABSENT);
                }
            }
        } catch (Exception e) {
            System.err.println("[CentralChecker] cache warm failed: " + e.getMessage());
        }
    }

    /** Memory only - safe to call from a cell value factory. */
    public CentralStatus statusOf(String coords) {
        if (coords == null || coords.isBlank()) return CentralStatus.NOT_MAVEN;
        return memory.getOrDefault(coords, CentralStatus.UNKNOWN);
    }

    /** Schedules a lookup if the answer is not already known. onResolved runs on a pool thread. */
    public void request(String coords, Consumer<String> onResolved) {
        if (coords == null || coords.isBlank()) return;
        var known = memory.get(coords);
        if (known == CentralStatus.PUBLISHED || known == CentralStatus.ABSENT) return;
        if (!inFlight.add(coords)) return;

        memory.put(coords, CentralStatus.CHECKING);
        pool.submit(() -> {
            try {
                var status = lookup(coords);
                memory.put(coords, status);
                persist(coords, status == CentralStatus.PUBLISHED);
            } catch (Exception e) {
                memory.remove(coords);
            } finally {
                inFlight.remove(coords);
                if (onResolved != null) onResolved.accept(coords);
            }
        });
    }

    CentralStatus lookup(String coords) {
        var parts = coords.split(":");
        if (parts.length != 3) return CentralStatus.NOT_MAVEN;
        var url = BASE + parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2]
                + "/" + parts[1] + "-" + parts[2] + ".pom";
        try {
            var req = HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(8))
                    .build();
            var code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            return code == 200 ? CentralStatus.PUBLISHED : CentralStatus.ABSENT;
        } catch (Exception e) {
            return CentralStatus.UNKNOWN;
        }
    }

    synchronized void persist(String coords, boolean published) {
        persistAll(Map.of(coords, published));
    }

    /** One transaction for many answers: a sweep produces hundreds, and one each is all overhead. */
    synchronized void persistAll(Map<String, Boolean> answers) {
        if (answers.isEmpty()) return;
        try {
            var db = db();
            var now = System.currentTimeMillis();
            db.transaction(() -> answers.forEach((coords, published) -> new CentralArtifact()
                    .coords(coords)
                    .published(published)
                    .checkedAt(now)
                    .in(db).whereEq($coords, coords).upsert()));
        } catch (Exception e) {
            System.err.println("[CentralChecker] persist failed for " + answers.size()
                    + " coordinates: " + e.getMessage());
        }
    }

    // ---------- whole-set sweep ----------

    public interface SweepProgress {
        void onProgress(int done, int total);
    }

    final AtomicBoolean sweeping = new AtomicBoolean();

    public boolean isSweeping() {
        return sweeping.get();
    }

    /**
     * Looks up every coordinate that is not already answered, so a "not on Central" filter can be
     * trusted.
     * <p>
     * Without this the column is viewport-lazy - only rows somebody scrolled past are ever checked -
     * which is right for painting a cell and useless for a question asked of the whole set. An
     * unchecked coordinate is UNKNOWN, and UNKNOWN must never be counted as ABSENT; sweeping is what
     * turns the unknowns into real answers rather than teaching the filter to guess.
     * <p>
     * Runs at the same four threads as everything else here and writes its answers in batches. The
     * cost is paid once: a published artifact is immutable on Central, so a positive answer is kept
     * forever and only negatives are ever asked again.
     */
    public void sweep(Collection<String> coordinates, SweepProgress progress, Runnable onDone) {
        if (!sweeping.compareAndSet(false, true)) return;

        var todo = coordinates.stream()
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .filter(c -> {
                    var known = memory.get(c);
                    return known != CentralStatus.PUBLISHED && known != CentralStatus.ABSENT;
                })
                .toList();

        if (todo.isEmpty()) {
            sweeping.set(false);
            if (progress != null) progress.onProgress(0, 0);
            if (onDone != null) onDone.run();
            return;
        }

        var done = new AtomicInteger();
        var total = todo.size();
        var pending = new ConcurrentLinkedQueue<Map.Entry<String, Boolean>>();

        new Thread(() -> {
            try {
                var futures = new ArrayList<Future<?>>(total);
                for (var coords : todo) {
                    if (!inFlight.add(coords)) continue;
                    memory.put(coords, CentralStatus.CHECKING);
                    futures.add(pool.submit(() -> {
                        try {
                            var status = lookup(coords);
                            memory.put(coords, status);
                            // UNKNOWN means the network failed, not that the artifact is absent, so
                            // it is deliberately not written - it must be asked again next time.
                            if (status == CentralStatus.PUBLISHED || status == CentralStatus.ABSENT) {
                                pending.add(Map.entry(coords, status == CentralStatus.PUBLISHED));
                            }
                        } catch (Exception e) {
                            memory.remove(coords);
                        } finally {
                            inFlight.remove(coords);
                            var n = done.incrementAndGet();
                            if (progress != null && (n % 10 == 0 || n == total)) progress.onProgress(n, total);
                            if (pending.size() >= PERSIST_BATCH) drain(pending);
                        }
                    }));
                }
                for (var f : futures) {
                    try {
                        f.get();
                    } catch (Exception e) {
                        // one failed lookup does not end the sweep
                    }
                }
            } finally {
                drain(pending);
                sweeping.set(false);
                if (onDone != null) onDone.run();
            }
        }, "CentralSweep").start();
    }

    static final int PERSIST_BATCH = 100;

    void drain(ConcurrentLinkedQueue<Map.Entry<String, Boolean>> pending) {
        var batch = new java.util.LinkedHashMap<String, Boolean>();
        for (Map.Entry<String, Boolean> e; (e = pending.poll()) != null; ) batch.put(e.getKey(), e.getValue());
        persistAll(batch);
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    Database db() {
        return DB.get().getDatabase();
    }
}
