package littlejlib.proj.daemon;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.group.Groups;
import littlejlib.proj.workspace.WorkspaceRefresh;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Re-walks the plain groups on a clock, because their bases cannot be watched.
 * <p>
 * A code base is local disk and a {@code WatchService} tells us the moment anything moves. A plain
 * base is Google Drive's virtual filesystem, where there is no event to receive; hooking Drive's
 * own change feed would be a second authenticated service inside this daemon for a list of folder
 * names. So: a clock, and a key on the tab for when the clock is not soon enough.
 */
public final class WorkspaceAutoRefresh {

    /**
     * How often the clock is CONSULTED, which is not how often a scan happens - {@code due} decides
     * that from what is in the index. Checking more often than the interval is what makes a machine
     * that was asleep for six hours refresh shortly after it wakes rather than up to an hour later.
     */
    static final int CHECK_MINUTES = 5;

    final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "Workspace-Auto-Refresh");
        t.setDaemon(true);
        return t;
    });

    public void start() {
        var settings = new SettingsManager().loadSettings();
        var minutes = WorkspaceRefresh.minutes(settings);
        if (settings == null || Groups.plain(settings).isEmpty()) return;
        if (minutes <= 0) {
            Server.log("plain groups: automatic refresh is off (workspaceRefreshMinutes = 0)");
            return;
        }
        Server.log("plain groups: re-walking every " + minutes + " min");
        // Not at startup: the first seconds belong to the database, the window and the tray icon,
        // and a walk of a network drive competing with them is a daemon that is slow to be ready.
        pool.scheduleWithFixedDelay(this::tick, 2, CHECK_MINUTES, TimeUnit.MINUTES);
    }

    /** Settings are re-read every tick, so a changed interval takes effect without a restart. */
    void tick() {
        try {
            var settings = new SettingsManager().loadSettings();
            var minutes = WorkspaceRefresh.minutes(settings);
            if (settings == null || minutes <= 0) return;
            for (var group : Groups.plain(settings)) {
                if (!WorkspaceRefresh.due(group, minutes)) continue;
                var t0 = System.currentTimeMillis();
                // Not forced: an automatic walk that comes back nearly empty is a drive that is
                // reconnecting, and it must not be allowed to sweep the index - see WorkspaceRefresh.
                var rows = WorkspaceRefresh.run(group, false, null, note -> Server.log("  " + note));
                Server.log("plain group '" + group.slug() + "': " + rows.size() + " folders in "
                        + (System.currentTimeMillis() - t0) + " ms");
                Server.touched();
            }
        } catch (Exception e) {
            Server.log("plain group refresh failed: " + e);
        }
    }

    public void stop() {
        pool.shutdownNow();
    }
}
