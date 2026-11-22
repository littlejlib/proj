package littlejlib.proj.workspace;

import littlejlib.proj.Settings;
import littlejlib.proj.group.ProjectGroup;
import littlejlib.proj.scanner.ScanProgress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The one place a PLAIN group is re-walked and stored, and the clock that says when it is due.
 * <p>
 * A plain base is a network filesystem - Google Drive's virtual one, in the case this was built
 * for - so there is no watcher to hang off and no change event to receive. What is left is a clock
 * and a key, which is what this is.
 */
public final class WorkspaceRefresh {

    public static final int DEFAULT_MINUTES = 60;

    /** Below this a walk measured in tens of seconds runs more often than anyone reads its result. */
    public static final int MIN_MINUTES = 5;

    /**
     * An automatic scan returning less than a quarter of what is already indexed is treated as a
     * failed walk rather than a deletion. G: answers a listing with an empty one while it is
     * reconnecting, and {@link WorkspaceStore#replaceAll} would take that literally and sweep the
     * index of an entire base. A FORCED scan writes whatever it finds - see {@link #run}.
     */
    static final int COLLAPSE_RATIO = 4, COLLAPSE_FLOOR = 20;

    private WorkspaceRefresh() { }

    static final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    static final Set<String> running = ConcurrentHashMap.newKeySet();

    /** What this process knows of each group's last write, so the clock never has to ask the index again. */
    static final Map<String, Long> scannedAt = new ConcurrentHashMap<>();

    /** Called with the group slug once new rows are stored, on the scanning thread. */
    public static void onRefreshed(Consumer<String> listener) {
        if (listener != null) listeners.add(listener);
    }

    public static boolean isRunning(ProjectGroup group) {
        return group != null && running.contains(group.slug());
    }

    /**
     * Walks every reachable base of the group, rewrites its rows and returns the whole group as it
     * now stands. An unreachable base is left alone rather than emptied: a drive that is merely not
     * mounted this minute is not a drive whose contents were deleted.
     */
    public static List<WorkspaceRecord> run(ProjectGroup group, boolean forced,
                                            ScanProgress progress, Consumer<String> note) {
        var store = new WorkspaceStore();
        if (group == null) return List.of();
        if (!running.add(group.slug())) {
            say(note, "already scanning " + group.displayLabel());
            return store.loadGroup(group.slug());
        }
        try {
            var stamp = System.currentTimeMillis();
            for (var base : group.basePaths()) scanBase(group, base, forced, progress, note, store, stamp);
            scannedAt.put(group.slug(), stamp);
            listeners.forEach(l -> l.accept(group.slug()));
            return store.loadGroup(group.slug());
        } finally {
            running.remove(group.slug());
        }
    }

    static void scanBase(ProjectGroup group, Path base, boolean forced, ScanProgress progress,
                         Consumer<String> note, WorkspaceStore store, long stamp) {
        if (!Files.isDirectory(base)) {
            say(note, "not reachable, left as it was: " + base);
            return;
        }
        var before = store.count(base);
        var scanned = new WorkspaceScanner(group.depth()).group(group.slug())
                .progress(progress == null ? ScanProgress.NONE : progress).scan(base);
        if (!forced && collapsed(before, scanned.size())) {
            say(note, "only " + scanned.size() + " of " + before + " folders came back from " + base
                    + " - kept the old rows, press refresh to write this anyway");
            return;
        }
        store.replaceAll(base, scanned, stamp);
        say(note, scanned.size() + "  " + base);
    }

    static boolean collapsed(long before, int now) {
        return before >= COLLAPSE_FLOOR && (long) now * COLLAPSE_RATIO < before;
    }

    /**
     * When this group was last written, 0 if it has never been scanned, and NEGATIVE if the index
     * could not be read. The three are kept apart because they lead different places: an unreadable
     * index must not present as "never scanned" and send a network drive off on a walk whose result
     * there is nowhere to put.
     */
    public static long lastScanned(ProjectGroup group) {
        try {
            return group == null ? 0L : new WorkspaceStore().lastScanned(group.slug());
        } catch (Exception e) {
            System.err.println("[WorkspaceRefresh] could not read the last scan time: " + e);
            return -1L;
        }
    }

    /** How often to re-walk, honouring the floor. 0 means the automatic half is switched off. */
    public static int minutes(Settings settings) {
        var m = settings == null ? DEFAULT_MINUTES : settings.getWorkspaceRefreshMinutes();
        return m <= 0 ? 0 : Math.max(MIN_MINUTES, m);
    }

    public static boolean due(ProjectGroup group, int minutes) {
        if (minutes <= 0 || group == null) return false;
        var last = scannedAt.getOrDefault(group.slug(), -1L);
        if (last < 0 && (last = lastScanned(group)) >= 0) scannedAt.put(group.slug(), last);
        if (last < 0) return false;
        return last == 0 || System.currentTimeMillis() - last >= minutes * 60_000L;
    }

    static void say(Consumer<String> note, String line) {
        if (note != null) note.accept(line);
    }
}
