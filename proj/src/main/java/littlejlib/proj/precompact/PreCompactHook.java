package littlejlib.proj.precompact;

import module java.base;

import com.google.gson.Gson;

import static littlejlib.proj.precompact.PreCompactState.*;

/**
 * The PreCompact hook. An automatic compaction is blocked (exit 2) and handed to a detached worker,
 * because hibernating the tab ends the very claude this hook runs under. A manual /compact is never touched.
 */
public final class PreCompactHook {

    private PreCompactHook() { }

    /** @return the hook's exit code */
    public static int run(InputStream stdin) {
        var in = PreCompactInput.read(stdin);
        if (in == null) return 0;
        if (!in.auto()) return 0;
        if (!take(in.sessionId())) {
            System.err.println("proj: automatic compaction blocked; a PRP status update and hibernation of this tab are already under way");
            return 2;
        }
        try {
            Files.writeString(job(in.sessionId()), new Gson().toJson(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log(in.sessionId() + "  cannot write the job: " + e);
            release(in.sessionId());
            return 0;
        }
        if (!startWorker(in.sessionId())) {
            release(in.sessionId());
            return 0;
        }
        log(in.sessionId() + "  automatic compaction blocked, worker started");
        System.err.println("proj: automatic compaction blocked. This conversation's PRP status will be updated and the tab "
                + "hibernated; `proj prp resume` continues it or starts fresh.");
        return 2;
    }

    /** Alone in a new hidden console: a child left on the tab's console dies with it when the tab hibernates. */
    static boolean startWorker(String session) {
        try {
            var win = System.getProperty("os.name", "").toLowerCase().contains("win");
            var argv = win
                    ? List.of("powershell", "-NoProfile", "-NonInteractive", "-Command",
                            "Start-Process -WindowStyle Hidden -FilePath proj -ArgumentList 'prp','precompact','--worker','" + session + "'")
                    : List.of("sh", "-c", "setsid nohup proj prp precompact --worker " + session + " >/dev/null 2>&1 &");
            var p = new ProcessBuilder(argv).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0) return true;
            log(session + "  could not start the worker (exit " + (p.isAlive() ? "timeout" : p.exitValue()) + ")");
        } catch (IOException | InterruptedException e) {
            log(session + "  could not start the worker: " + e);
        }
        return false;
    }
}
