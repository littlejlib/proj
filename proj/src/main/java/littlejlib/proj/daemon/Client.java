package littlejlib.proj.daemon;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;

/**
 * The thin half. Sends argv to the daemon and returns what comes back.
 * <p>
 * If there is no daemon it starts one. If the daemon is running older code than this client, it is
 * told to stop and a fresh one takes its place - otherwise a rebuilt jar keeps being ignored by a
 * daemon nobody remembers starting.
 */
public final class Client {

    private Client() { }

    public static final int CONNECT_TIMEOUT_MS = 3000, READ_TIMEOUT_MS = 900_000;

    public static Response send(List<String> args, boolean autoStart, Consumer<String> log) throws IOException {
        var info = Info.read();
        if (info == null || !info.processAlive()) {
            if (!autoStart) throw new ConnectException("no daemon running");
            // Said UNCONDITIONALLY, not behind --verbose: this is the one wait in the whole tool with
            // no window and no progress bar to explain it, and the caller's own `log` is silent unless
            // -v was passed. Without this line the whole multi-second cold start (database, JavaFX,
            // the first Stage and Scene - see Server.run's own timing) looks like a hang rather than
            // work in progress. Goes to stderr, via LogConfig so it survives --json, so a script
            // reading stdout is untouched.
            littlejlib.proj.LogConfig.err().println(
                    "[proj] no project daemon running yet - starting one; the first open can take a few "
                            + "seconds while it warms up");
            info = Launcher.startAndWait(log);
        }

        var response = attempt(info, args);
        if (response.staleBuild) {
            log.accept("daemon is running " + response.stamp + ", this client is " + Build.STAMP + " - restarting it");
            Launcher.awaitExit(info, log);
            info = Launcher.startAndWait(log);
            response = attempt(info, args);
        }
        return response;
    }

    static Response attempt(Info info, List<String> args) throws IOException {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), info.port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            Protocol.write(socket.getOutputStream(),
                    new Request(Build.STAMP, System.getProperty("user.dir"), args));
            return Protocol.readResponse(socket.getInputStream());
        }
    }

    /** Null when nothing is listening. */
    public static String ping() {
        var info = Info.read();
        if (info == null || !info.processAlive()) return null;
        try {
            return attempt(info, List.of("--ping")).stdout.trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean stop(Consumer<String> log) {
        var info = Info.read();
        if (info == null) return false;
        try {
            attempt(info, List.of("--stop"));
        } catch (IOException e) {
            log.accept("daemon did not answer a stop request (" + e.getMessage() + ")");
        }
        Launcher.awaitExit(info, log);
        Info.clear();
        return true;
    }
}
