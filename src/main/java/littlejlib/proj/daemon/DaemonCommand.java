package littlejlib.proj.daemon;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import littlejlib.proj.util.AppPaths;

import java.util.List;

/**
 * Control for the resident half, because a daemon you cannot inspect or stop is a daemon you end
 * up killing from Task Manager.
 */
@Command(name = "daemon", description = "Inspect or control the background project daemon")
public class DaemonCommand implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "status | start | stop | restart | log")
    String action = "status";

    @Override
    public void run() {
        switch (action == null ? "status" : action.toLowerCase()) {
            case "status" -> status();
            case "start" -> start();
            case "stop" -> stop();
            case "restart" -> { stop(); start(); }
            case "log" -> System.out.println(AppPaths.daemonLog());
            default -> System.out.println("unknown action: " + action + "  (status | start | stop | restart | log)");
        }
    }

    void status() {
        var pong = Client.ping();
        if (pong != null) {
            System.out.println(pong);
            System.out.println("this client is " + Build.STAMP
                    + (pong.contains(Build.STAMP) ? "  (same build)" : "  (DIFFERENT build - it will restart on next use)"));
            return;
        }
        var info = Info.read();
        System.out.println(info == null ? "no daemon running"
                : "a daemon record exists for pid " + info.pid + " but nothing answers; it will be replaced on next use");
    }

    void start() {
        if (Client.ping() != null) {
            System.out.println("already running");
            return;
        }
        try {
            var info = Launcher.startAndWait(m -> System.out.println("[proj] " + m));
            System.out.println("started on port " + info.port + ", pid " + info.pid);
        } catch (Exception e) {
            System.err.println("could not start: " + e.getMessage());
        }
    }

    void stop() {
        System.out.println(Client.stop(m -> System.out.println("[proj] " + m)) ? "stopped" : "nothing to stop");
    }

    static List<String> nothing() {
        return List.of();
    }
}
