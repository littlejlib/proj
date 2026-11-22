package littlejlib.proj.precompact;

import module java.base;

import static littlejlib.proj.precompact.PreCompactState.log;

/** Has the session's own claude write its PRP status file: type the request into the tab, wait for the turn to end. */
public final class StatusUpdate {

    private StatusUpdate() { }

    static final long POLL_MS = 2000, START_WAIT_S = 90, TURN_WAIT_MIN = 20;

    /** @return the tab once it is idle, or empty when it never gets there in time */
    public static Optional<TabInfo> waitIdle(String session, long maxSeconds) {
        var until = System.currentTimeMillis() + maxSeconds * 1000;
        while (System.currentTimeMillis() < until) {
            var tab = Tabs.byClaudeSession(session);
            if (tab.isEmpty()) return Optional.empty();
            if (tab.get().idle()) return tab;
            sleep();
        }
        return Optional.empty();
    }

    static String request(Path statusFile) {
        return "Automatic compaction was just blocked by the PreCompact hook, and this tab hibernates when you finish. "
                + "Update the PRP status file " + statusFile + " now: terse, what is done, tested, decided, open and next, "
                + "following the rules in the PRP. Do nothing else and ask nothing.";
    }

    /** @return true when the turn ran and ended; the file's own change is reported separately */
    public static boolean run(String session, String tabId, Path statusFile) {
        var before = stamp(statusFile);
        if (!Tabs.send(tabId, request(statusFile))) { log(session + "  could not type into the tab"); return false; }
        var started = waitFor(session, true, START_WAIT_S);
        if (!started) { log(session + "  the status request did not start a turn within " + START_WAIT_S + " s"); return false; }
        var ended = waitFor(session, false, TURN_WAIT_MIN * 60);
        log(session + "  status turn " + (ended ? "ended" : "did not end in time") + "; status file "
                + (stamp(statusFile) == before ? "UNCHANGED" : "written"));
        return ended;
    }

    /** @param busy wait until the tab is busy (true) or idle (false); a vanished tab ends the wait */
    static boolean waitFor(String session, boolean busy, long maxSeconds) {
        var until = System.currentTimeMillis() + maxSeconds * 1000;
        while (System.currentTimeMillis() < until) {
            var tab = Tabs.byClaudeSession(session);
            if (tab.isEmpty()) return false;
            if (busy ? tab.get().busy() : tab.get().idle()) return true;
            sleep();
        }
        return false;
    }

    static long stamp(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    static void sleep() {
        try { Thread.sleep(POLL_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
