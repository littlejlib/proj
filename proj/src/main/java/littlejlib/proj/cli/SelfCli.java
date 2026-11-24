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
            var self = Native.image() ? Native.exe() : null;
            var r = Install.uninstall("proj", m -> { if (!isOwnAside(m, self)) System.out.println(m); });
            var problems = r.problems().stream().filter(p -> !isOwnAside(p, self)).toList();
            problems.forEach(p -> System.err.println("proj: " + p));
            if (self != null) deleteAfterExit(self);
            if (!problems.isEmpty()) System.exit(1);
        }
    }

    // Workaround for PRP jarrunner-001V: jr-apputils renames the running exe aside, then tries to delete that
    // same running file. Delete it once this process has exited instead. Remove when jr-apputils does this.
    static boolean isOwnAside(String message, java.nio.file.Path self) {
        return self != null && message.contains(self.getFileName() + ".jr-replaced");
    }

    /** A detached cmd waits for this process to end, then removes the exe, its set-aside copy and an empty folder. */
    static void deleteAfterExit(java.nio.file.Path exe) {
        try {
            var aside = exe.resolveSibling(exe.getFileName() + ".jr-replaced");
            var script = "ping -n 3 127.0.0.1 >nul & del /f /q \"" + exe + "\" \"" + aside + "\" 2>nul & rmdir \""
                    + exe.getParent() + "\" 2>nul";
            new ProcessBuilder("cmd", "/c", script).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (Exception e) {
            System.err.println("proj: could not schedule removal of " + exe + ": " + e.getMessage());
        }
    }
}
