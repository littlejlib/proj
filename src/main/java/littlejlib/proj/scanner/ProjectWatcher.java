package littlejlib.proj.scanner;

import littlejlib.proj.model.ProjectRecord;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Keeps the stored rows honest while the process is alive, so a resident daemon never serves a
 * cache that quietly went stale.
 * <p>
 * Registers a watch on every directory the scanner would descend into. A new subdirectory is
 * described and upserted, a removed one is deleted, and a touched pom.xml re-reads that project.
 * Events are coalesced over a short window because an IDE writing a pom emits several.
 */
public class ProjectWatcher {

    public static final long QUIET_PERIOD_MS = 750;

    final ProjectStore store = new ProjectStore();
    final ProjectTreeScanner scanner;
    final int maxDepth;
    final Map<WatchKey, Path> watched = new HashMap<>();
    final Map<Path, Path> codeDirOf = new HashMap<>();
    final Map<Path, Integer> depthOf = new HashMap<>();

    WatchService service;
    Thread thread;
    volatile boolean running;
    Consumer<List<ProjectRecord>> onChange = records -> { };

    public ProjectWatcher(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
        this.scanner = new ProjectTreeScanner(maxDepth);
    }

    public ProjectWatcher onChange(Consumer<List<ProjectRecord>> listener) {
        if (listener != null) this.onChange = listener;
        return this;
    }

    public synchronized void start(List<Path> codeDirs) {
        if (running) return;
        try {
            service = FileSystems.getDefault().newWatchService();
        } catch (Exception e) {
            System.err.println("[ProjectWatcher] no watch service: " + e.getMessage());
            return;
        }
        running = true;
        for (var cd : codeDirs) register(cd, cd, 0);

        thread = new Thread(this::loop, "ProjectWatcher");
        thread.setDaemon(true);
        thread.start();
    }

    void register(Path dir, Path codeDir, int depth) {
        if (depth > maxDepth || !Files.isDirectory(dir)) return;
        try {
            var key = dir.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            watched.put(key, dir);
            codeDirOf.put(dir, codeDir);
            depthOf.put(dir, depth);
        } catch (Exception e) {
            return;
        }
        if (depth == maxDepth) return;
        for (var child : scanner.childDirs(dir)) register(child, codeDir, depth + 1);
    }

    void loop() {
        while (running) {
            WatchKey key;
            try {
                key = service.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (key == null) continue;

            var touched = new ArrayList<Path>();
            drain(key, touched);

            // Coalesce: an editor saving a pom emits several events in a row.
            try {
                Thread.sleep(QUIET_PERIOD_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            WatchKey extra;
            while ((extra = service.poll()) != null) drain(extra, touched);

            if (!touched.isEmpty()) apply(touched);
        }
    }

    void drain(WatchKey key, List<Path> touched) {
        var dir = watched.get(key);
        if (dir != null) {
            for (var event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) continue;
                touched.add(dir.resolve(event.context().toString()));
            }
        }
        if (!key.reset()) watched.remove(key);
    }

    void apply(List<Path> touched) {
        // Dedupe by the DIRECTORY each event resolves to, not by the raw event path: creating a
        // project emits one event for the directory and another for the pom.xml inside it, which
        // are two distinct paths describing one change.
        var dirs = new java.util.LinkedHashSet<Path>();
        for (var path : touched) {
            var dir = Files.isDirectory(path) ? path : path.getParent();
            if (dir != null) dirs.add(dir);
        }

        var updated = new ArrayList<ProjectRecord>();
        for (var dir : dirs) {
            var codeDir = nearestCodeDir(dir);
            if (codeDir == null) continue;

            if (!Files.isDirectory(dir)) {
                store.deleteByPath(dir.toAbsolutePath().toString());
                continue;
            }

            var depth = depthOf.getOrDefault(dir, 1);
            // describe() appends to the scanner's dependency list, which is designed to accumulate
            // across a whole scan. This watcher lives for the life of the daemon, so the list is
            // emptied first and read straight afterwards - otherwise it grows without bound and
            // every project's dependencies get written to every project that changes.
            scanner.dependencies().clear();
            var rec = scanner.describe(dir, codeDir, parentPathOf(dir, codeDir), depth);
            if (rec.isProject()) {
                var deps = List.copyOf(scanner.dependencies());
                var existing = store.byPath(rec.path());

                // A pom edit that adds or removes a dependency leaves the record's signature
                // identical - the coordinates, packaging and description are all unchanged - so the
                // dependency list has to be compared in its own right or that edit is silently
                // dropped and the concern columns keep showing the previous answer.
                var sameRecord = existing != null && existing.signature().equals(rec.signature());
                var sameDeps = !rec.maven()
                        || fingerprint(deps).equals(fingerprint(store.dependenciesOf(rec.path())));
                if (sameRecord && sameDeps) continue;

                store.save(rec);
                if (rec.maven()) store.replaceDependencies(List.of(rec.path()), deps);
                updated.add(rec);
                if (!depthOf.containsKey(dir)) register(dir, codeDir, depth);
            } else if (store.byPath(rec.path()) != null) {
                store.deleteByPath(rec.path());
                store.replaceDependencies(List.of(rec.path()), List.of());
            }
        }
        if (!updated.isEmpty()) onChange.accept(updated);
    }

    /** Order-independent, so a reordered pom is not mistaken for a changed one. */
    static String fingerprint(List<littlejlib.proj.model.ProjectDependency> deps) {
        return deps.stream()
                .map(d -> d.ga() + "@" + (d.version() == null ? "?" : d.version()) + "/" + nz(d.scope())
                        + (d.optional() ? "/opt" : ""))
                .sorted().collect(java.util.stream.Collectors.joining(","));
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    Path nearestCodeDir(Path dir) {
        for (var p = dir; p != null; p = p.getParent()) {
            var cd = codeDirOf.get(p);
            if (cd != null) return cd;
        }
        return null;
    }

    String parentPathOf(Path dir, Path codeDir) {
        for (var p = dir.getParent(); p != null && p.startsWith(codeDir); p = p.getParent()) {
            if (store.byPath(p.toAbsolutePath().toString()) != null) return p.toAbsolutePath().toString();
        }
        return "";
    }

    public synchronized void stop() {
        running = false;
        try {
            if (service != null) service.close();
        } catch (Exception e) {
            // closing a watch service that already failed is not interesting
        }
    }
}
