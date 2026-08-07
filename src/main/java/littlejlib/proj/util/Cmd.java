package littlejlib.proj.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Splitting a command template into argv and running it. The same parser was copy-pasted in Main
 * and in the picker; it lives here now.
 */
public final class Cmd {

    private Cmd() { }

    public static final String PATH_PLACEHOLDER = "%PATH%";

    /** Splits on spaces, honouring double quotes. */
    public static String[] parse(String command) {
        var parts = new ArrayList<String>();
        var current = new StringBuilder();
        var inQuotes = false;

        for (var i = 0; i < command.length(); i++) {
            var c = command.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    /** Substitutes %PATH% and launches. Returns false rather than throwing. */
    public static boolean runTemplate(String template, Path target) {
        if (template == null || template.isBlank()) return false;
        return run(parse(template.replace(PATH_PLACEHOLDER, target.toAbsolutePath().toString())), null);
    }

    public static boolean run(String[] argv, Path workingDir) {
        if (argv == null || argv.length == 0) return false;
        try {
            var pb = new ProcessBuilder(argv);
            if (workingDir != null) pb.directory(workingDir.toFile());
            pb.start();
            return true;
        } catch (IOException e) {
            System.err.println("[Cmd] failed to run " + List.of(argv) + ": " + e.getMessage());
            return false;
        }
    }
}
