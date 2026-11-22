package littlejlib.proj.daemon;

/**
 * What comes back.
 * <p>
 * {@code chosenPath} rather than "the daemon changed your directory": PROJ_POSTRUN_BATCH belongs to
 * the shell that launched the client, and proj.bat runs that script after the client exits. A
 * daemon started from a different shell cannot reach it. So the daemon decides, the client acts.
 * <p>
 * {@code action} is the same principle applied to the main menu. The daemon can show the menu -
 * that is the point of it holding a warm JavaFX - but some of what the menu leads to prompts on a
 * terminal, and the daemon has no tty. Those come back named, for the client to run itself.
 */
public class Response {

    public int exitCode;
    public String stdout = "";
    public String stderr = "";
    public String chosenPath;
    public String action;
    public boolean staleBuild;
    public String stamp;

    public static Response ok(String stdout) {
        var r = new Response();
        r.stdout = stdout == null ? "" : stdout;
        return r;
    }

    public static Response chose(String absolutePath) {
        var r = new Response();
        r.chosenPath = absolutePath;
        return r;
    }

    /** The user picked something only the caller's process can carry out. */
    public static Response act(String action) {
        var r = new Response();
        r.action = action;
        return r;
    }

    public static Response error(int code, String message) {
        var r = new Response();
        r.exitCode = code;
        r.stderr = message == null ? "" : message;
        return r;
    }

    public static Response stale(String daemonStamp) {
        var r = new Response();
        r.staleBuild = true;
        r.stamp = daemonStamp;
        return r;
    }
}
