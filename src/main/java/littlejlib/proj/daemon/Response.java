package littlejlib.proj.daemon;

/**
 * What comes back.
 * <p>
 * {@code chosenPath} rather than "the daemon changed your directory": PROJ_POSTRUN_BATCH belongs to
 * the shell that launched the client, and proj.bat runs that script after the client exits. A
 * daemon started from a different shell cannot reach it. So the daemon decides, the client acts.
 */
public class Response {

    public int exitCode;
    public String stdout = "";
    public String stderr = "";
    public String chosenPath;
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
