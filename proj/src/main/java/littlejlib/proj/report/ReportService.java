package littlejlib.proj.report;

import littlejlib.proj.track.WorkGraph;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports as the daemon serves them. Work items are read on every request (one small file per project, so a change
 * shows at once); the PRPs of each folder, which cost one read per PRP on a network drive, are kept for a time limit
 * that the caller may shorten ({@code maxAge}, 0 = read now). A write through the page drops that project's entry.
 */
public final class ReportService {

    record Cached(Instant at, Map<String, WorkGraph.Prp> prps) { }

    final Map<Path, Cached> cache = new ConcurrentHashMap<>();
    volatile int cacheMinutes;

    public ReportService(int cacheMinutes) {
        this.cacheMinutes = cacheMinutes;
    }

    public void cacheMinutes(int minutes) {
        this.cacheMinutes = minutes;
    }

    public ReportData report(ReportQuery q) throws IOException {
        var limit = Duration.ofMinutes(q.maxAgeMinutes() != null ? q.maxAgeMinutes() : cacheMinutes);
        var oldest = new Instant[]{Instant.now()};
        WorkGraph.PrpSource source = prpDir -> {
            var c = cache.get(prpDir);
            if (c == null || Duration.between(c.at(), Instant.now()).compareTo(limit) >= 0) {
                c = new Cached(Instant.now(), WorkGraph.readPrps(prpDir));
                cache.put(prpDir, c);
            }
            if (c.at().isBefore(oldest[0])) oldest[0] = c.at();
            return c.prps();
        };
        var data = ReportBuilder.build(q, null, source);
        data.cacheAge = Duration.between(oldest[0], Instant.now()).toMinutes();
        return data;
    }

    /** After a write: the next report reads this project's PRPs again. */
    public void invalidate(Path projectDir) {
        cache.remove(projectDir.resolve("prp"));
    }
}
