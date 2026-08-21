package littlejlib.proj.daemon;

import java.util.List;

/**
 * What the thin client asks for. {@code cwd} travels because the daemon was started from some
 * other directory, possibly days ago, and "where am I" is the client's fact, not the daemon's.
 */
public class Request {

    public String stamp;
    public String cwd;
    public List<String> args;

    public Request() { }

    public Request(String stamp, String cwd, List<String> args) {
        this.stamp = stamp;
        this.cwd = cwd;
        this.args = args;
    }

    public String verb() {
        return args == null || args.isEmpty() ? "" : args.get(0);
    }
}
