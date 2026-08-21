package littlejlib.proj.daemon;

import com.google.gson.Gson;
import littlejlib.proj.util.AppPaths;

import java.nio.file.Files;

/**
 * The running daemon's port and pid, on disk, so a fresh client can find it.
 * <p>
 * The pid matters as much as the port: a stale file after a crash would otherwise send clients to
 * a port nobody is listening on, and the timeout is far worse than starting a daemon.
 */
public class Info {

    public int port;
    public long pid;
    public String stamp;

    static final Gson GSON = new Gson();

    public Info() { }

    public Info(int port, long pid, String stamp) {
        this.port = port;
        this.pid = pid;
        this.stamp = stamp;
    }

    public boolean processAlive() {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    public static Info read() {
        try {
            var file = AppPaths.daemonInfo();
            if (!Files.isRegularFile(file)) return null;
            return GSON.fromJson(Files.readString(file), Info.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void write() {
        try {
            var file = AppPaths.daemonInfo();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (Exception e) {
            System.err.println("[daemon] could not record daemon info: " + e.getMessage());
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(AppPaths.daemonInfo());
        } catch (Exception e) {
            // a leftover file is handled by the pid check on the next read
        }
    }
}
