package littlejlib.proj.track;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * The project prose that headed an old {@code 00-prp.index.md} (rules, background) moves to
 * {@code prp/project_preamble.md}, and the project's CLAUDE.md gets one line naming it, without {@code @}
 * so it is read only when needed, and saying it may be outdated.
 */
public final class Preamble {

    private Preamble() { }

    public static final String FILE = "project_preamble.md";

    static final String RULE = "- Project preamble: the project rules and background that used to head the PRP index are in `prp/"
            + FILE + "`. Read it when you need them. It may be outdated: the code, the PRPs and their status are newer, and win.";

    /** The lines before the first PRP line, or none when that is fewer than three lines of text. */
    public static List<String> leading(List<String> lines) {
        var end = 0;
        while (end < lines.size() && !IndexMigrate.TOP.matcher(lines.get(end)).matches()) end++;
        var head = lines.subList(0, end);
        return head.stream().filter(l -> !l.isBlank()).count() < 3 ? List.of() : head;
    }

    /** Writes the preamble and the CLAUDE.md line; says what it did (or would do). Never overwrites a preamble. */
    public static String move(Path prpDir, List<String> preamble, boolean dryRun) throws IOException {
        if (preamble.isEmpty()) return "no preamble";
        var target = prpDir.resolve(FILE);
        var claude = prpDir.toAbsolutePath().normalize().getParent().resolve("CLAUDE.md");
        var named = Files.isRegularFile(claude) && Files.readString(claude, StandardCharsets.UTF_8).contains(FILE);
        var say = (dryRun ? "would move " : "moved ") + preamble.size() + " preamble lines to " + FILE
                + (named ? "" : (dryRun ? ", would name it in " : ", named it in ") + (Files.exists(claude) ? "CLAUDE.md" : "a new CLAUDE.md"));
        if (Files.exists(target)) return FILE + " exists, left as is; preamble stays in the renamed old index";
        if (dryRun) return say;
        Files.writeString(target, String.join("\n", preamble).strip() + "\n", StandardCharsets.UTF_8);
        if (!named) {
            var gap = Files.isRegularFile(claude) && !Files.readString(claude, StandardCharsets.UTF_8).endsWith("\n") ? "\n\n" : "\n";
            Files.writeString(claude, (Files.exists(claude) ? gap : "") + RULE + "\n", StandardCharsets.UTF_8, CREATE, APPEND);
        }
        return say;
    }
}
