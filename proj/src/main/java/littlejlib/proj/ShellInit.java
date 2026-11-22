package littlejlib.proj;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Emits the one-line shell adapter, the way `zoxide init` does. The adapter is what
 * actually performs the cd, because only the shell can move itself.
 */
public final class ShellInit {

    static final String USAGE = "usage: proj --init clink|cmd|powershell|bash";

    public static String resourceFor(String kind) {
        return switch (kind == null ? "" : kind.trim().toLowerCase()) {
            case "clink" -> "clink.lua";
            case "cmd", "doskey" -> "cmd.txt";
            case "powershell", "pwsh", "ps" -> "powershell.txt";
            case "bash", "zsh", "sh" -> "bash.txt";
            default -> null;
        };
    }

    /** The adapter text, ready to write. */
    public static String render(String kind) throws IOException {
        var file = resourceFor(kind);
        if (file == null) throw new IOException("unknown adapter: " + kind);
        return load(file).replace("{TOOL}", tool());
    }

    public static int emit(String kind) {
        if (resourceFor(kind) == null) {
            System.err.println(USAGE);
            return 2;
        }
        try {
            StdoutChannel.data().print(render(kind));
            StdoutChannel.data().flush();
            return 0;
        } catch (IOException e) {
            System.err.println("could not read adapter template for " + kind + ": " + e.getMessage());
            return 1;
        }
    }

    static String tool() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "proj.exe" : "proj-bin";
    }

    static String load(String file) throws IOException {
        var path = "/shell-init/" + file;
        try (var in = ShellInit.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("resource missing: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ShellInit() {}
}
