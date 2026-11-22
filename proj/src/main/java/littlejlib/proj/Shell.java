package littlejlib.proj;

import java.nio.file.Path;

public final class Shell {

    public static String detect() {
        var p = ProcessHandle.current().parent();
        for (var lvl = 0; p.isPresent() && lvl < 4; lvl++) {
            var exe = p.get().info().command().orElse("").toLowerCase();
            var name = exe.substring(Math.max(exe.lastIndexOf('\\'), exe.lastIndexOf('/')) + 1);
            if (name.startsWith("cmd.")) return "cmd";
            if (name.startsWith("powershell.") || name.startsWith("pwsh.")) return "powershell";
            if (name.startsWith("bash.") || name.startsWith("zsh.") || name.startsWith("sh.")) return "bash";
            p = p.get().parent();
        }
        return "unknown";
    }

    public static String cdCommand(Path dir) {
        return cdCommand(detect(), dir);
    }

    /**
     * Separators are normalised because `cd /d "/foo/"` fails outright -- the /d flag
     * stops cd accepting forward slashes. Clink hit the same wall and left a comment
     * about it in make_cd_command (clink/lib/src/intercept.cpp).
     */
    public static String cdCommand(String shell, Path dir) {
        var d = dir.toAbsolutePath().normalize().toString();
        return switch (shell) {
            case "powershell" -> "Set-Location -LiteralPath '" + d.replace("'", "''") + "'";
            case "bash" -> "cd \"" + d + "\"";
            default -> "cd /d \"" + d + "\"";
        };
    }

    private Shell() {}
}
