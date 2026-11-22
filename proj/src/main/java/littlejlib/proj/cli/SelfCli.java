package littlejlib.proj.cli;

import jarrunner.jr.apputils.Install;
import jarrunner.jr.apputils.JrApp;
import jarrunner.jr.apputils.UpdateResult;
import jarrunner.jr.apputils.Updater;
import littlejlib.proj.daemon.Client;
import littlejlib.proj.self.NativeUpdate;
import littlejlib.proj.util.Native;
import picocli.CommandLine.Command;

/// `proj install`, `proj update`, `proj uninstall`: the downloaded exe puts itself in place and keeps itself
/// current (jr-apputils; jr checks the SHA-256 and swaps the exe). The daemon runs from the exe, so it is
/// stopped first; it starts again on the next use.
public final class SelfCli {
    private SelfCli() {}

    private static void stopDaemon() {
        try { Client.stop(m -> {}); } catch (Exception ignored) { }
    }

    @Command(name = "install", description = "Put this proj in your user folder and on PATH (no administrator rights)")
    public static class InstallCmd implements Runnable {
        @Override public void run() {
            stopDaemon();
            var r = Install.app("proj").displayName("proj").description("proj: every project, and the PRP tool")
                    .menuShortcut(false).run();
            if (!r.ok()) {
                r.problems().forEach(p -> System.err.println("proj: " + p));
                System.exit(1);
            }
            System.out.println("proj " + JrApp.appVersion() + " installed. Open a new terminal and run: proj prp guide");
        }
    }

    @Command(name = "update", description = "Update this proj to the newest release")
    public static class UpdateCmd implements Runnable {
        @Override public void run() {
            if (Native.image()) {
                var code = NativeUpdate.run(false, SelfCli::stopDaemon);
                if (code != 0) System.exit(code);
                return;
            }
            var check = Updater.check();
            if (check.status() == UpdateResult.Status.NEWER) stopDaemon();
            var r = check.status() == UpdateResult.Status.NEWER ? Updater.update() : check;
            System.out.println(r.message());
            if (!r.ok()) System.exit(1);
        }
    }

    @Command(name = "uninstall", description = "Remove what 'proj install' put in place (your projects and PRPs stay)")
    public static class UninstallCmd implements Runnable {
        @Override public void run() {
            stopDaemon();
            var r = Install.uninstall("proj");
            r.problems().forEach(p -> System.err.println("proj: " + p));
            if (!r.ok()) System.exit(1);
        }
    }
}
