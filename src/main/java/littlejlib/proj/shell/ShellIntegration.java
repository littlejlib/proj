package littlejlib.proj.shell;

import littlejlib.proj.ShellInit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Installing and removing the shell adapter that actually performs the cd.
 * <p>
 * Clink is a standalone script file, so install is a write and uninstall is a delete. The shell
 * profiles are shared files owned by the user, so their block is fenced with markers and uninstall
 * removes exactly that fence - never rewriting a profile wholesale.
 */
public final class ShellIntegration {

    private ShellIntegration() { }

    public static final String
            BEGIN = "# >>> proj shell integration >>>",
            END = "# <<< proj shell integration <<<";

    public static final List<String> KINDS = List.of("clink", "cmd", "powershell", "bash");

    /** Clink is only offered when it is actually present; otherwise the honest answer is cmd. */
    public static String autoDetect() {
        if (Clink.installed()) return "clink";
        var shell = littlejlib.proj.Shell.detect();
        return switch (shell) {
            case "powershell", "bash" -> shell;
            default -> "cmd";
        };
    }

    public static Path targetFor(String kind) {
        return switch (kind) {
            case "clink" -> Clink.installedScript();
            case "powershell" -> powershellProfile();
            case "bash" -> bashProfile();
            case "cmd" -> Path.of(System.getProperty("user.home"), "projinit.bat");
            default -> null;
        };
    }

    public static boolean isInstalled(String kind) {
        var target = targetFor(kind);
        if (target == null || !Files.isRegularFile(target)) return false;
        if (kind.equals("clink") || kind.equals("cmd")) return true;
        try {
            return Files.readString(target, StandardCharsets.UTF_8).contains(BEGIN);
        } catch (IOException e) {
            return false;
        }
    }

    public static String install(String kind) throws IOException {
        if (kind.equals("clink") && !Clink.installed())
            throw new IOException("Clink is not installed, so there is nothing to install into. "
                    + "Use `proj shell install cmd` instead, or install Clink first.");

        var target = targetFor(kind);
        if (target == null) throw new IOException("no install location known for " + kind);
        var body = ShellInit.render(kind);

        if (target.getParent() != null) Files.createDirectories(target.getParent());

        if (kind.equals("clink") || kind.equals("cmd")) {
            Files.writeString(target, body, StandardCharsets.UTF_8);
            return target.toString();
        }

        var existing = Files.isRegularFile(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
        var stripped = removeBlock(existing);
        var sep = stripped.isBlank() || stripped.endsWith("\n") ? "" : "\n";
        Files.writeString(target, stripped + sep + BEGIN + "\n" + body
                + (body.endsWith("\n") ? "" : "\n") + END + "\n", StandardCharsets.UTF_8);
        return target.toString();
    }

    public static String uninstall(String kind) throws IOException {
        var target = targetFor(kind);
        if (target == null) throw new IOException("no install location known for " + kind);
        if (!Files.isRegularFile(target)) return null;

        if (kind.equals("clink") || kind.equals("cmd")) {
            Files.delete(target);
            return target.toString();
        }

        var existing = Files.readString(target, StandardCharsets.UTF_8);
        if (!existing.contains(BEGIN)) return null;
        var remaining = removeBlock(existing);
        // If our block was the only thing in there, we created the file; leaving an empty profile
        // behind is not "uninstalled".
        if (remaining.isBlank()) Files.delete(target);
        else Files.writeString(target, remaining, StandardCharsets.UTF_8);
        return target.toString();
    }

    /** Drops one fenced block, leaving the rest of a user-owned profile untouched. */
    static String removeBlock(String text) {
        var start = text.indexOf(BEGIN);
        if (start < 0) return text;
        var end = text.indexOf(END, start);
        if (end < 0) return text.substring(0, start);
        var after = end + END.length();
        if (after < text.length() && text.charAt(after) == '\r') after++;
        if (after < text.length() && text.charAt(after) == '\n') after++;
        return text.substring(0, start) + text.substring(after);
    }

    static Path powershellProfile() {
        var reported = firstLineOf("powershell", "-NoProfile", "-Command", "$PROFILE");
        if (reported != null && !reported.isBlank()) return Path.of(reported.trim());
        return Path.of(System.getProperty("user.home"), "Documents", "WindowsPowerShell",
                "Microsoft.PowerShell_profile.ps1");
    }

    static Path bashProfile() {
        var home = Path.of(System.getProperty("user.home"));
        var rc = home.resolve(".bashrc");
        return Files.isRegularFile(rc) ? rc : home.resolve(".profile");
    }

    static String firstLineOf(String... command) {
        try {
            var pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            var proc = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                var line = reader.readLine();
                proc.waitFor();
                return line;
            }
        } catch (Exception e) {
            Thread.interrupted();
            return null;
        }
    }
}
