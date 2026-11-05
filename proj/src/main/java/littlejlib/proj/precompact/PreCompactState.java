package littlejlib.proj.precompact;

import module java.base;

import littlejlib.proj.util.AppPaths;

/** The hook's files under {@code ~/littlejlib/proj}: the log, and per session a lock and the job handed to the worker. */
public final class PreCompactState {

    private PreCompactState() { }

    /** A worker older than this is taken for dead, so one crash cannot block compaction handling for good. */
    static final long STALE_MINUTES = 45;

    public static Path dir() {
        return AppPaths.home().resolve("precompact");
    }

    public static Path logFile() {
        return AppPaths.home().resolve("precompact.log");
    }

    static Path lock(String session) {
        return dir().resolve(session + ".lock");
    }

    public static Path job(String session) {
        return dir().resolve(session + ".job.json");
    }

    public static synchronized void log(String line) {
        try {
            Files.createDirectories(logFile().getParent());
            Files.writeString(logFile(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + "  " + line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    /** @return false when a live worker already handles this session, which is what stops the status-update turn from starting another */
    public static boolean take(String session) {
        try {
            Files.createDirectories(dir());
            var lock = lock(session);
            if (Files.exists(lock) && System.currentTimeMillis() - Files.getLastModifiedTime(lock).toMillis() < STALE_MINUTES * 60_000) return false;
            Files.writeString(lock, Long.toString(System.currentTimeMillis()));
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    public static void release(String session) {
        try {
            Files.deleteIfExists(lock(session));
            Files.deleteIfExists(job(session));
        } catch (IOException ignored) { }
    }
}
