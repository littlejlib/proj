package littlejlib.proj.prp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The four names of a project: short name, 3-letter alias, 4-letter alias, full name. A project with no
 * {@code prp/proj.json} gets none of them invented: its first PRP is refused until they are given.
 * Existing files without aliases keep working; only a hint is printed.
 */
public final class ProjNames {

    private ProjNames() { }

    public static final String
            SHORT = "short name",
            ALIAS3 = "alias3",
            ALIAS4 = "alias4",
            FULL = "full name";

    /** The project a prp folder belongs to, or null when the folder is not called prp. */
    public static Path projectOf(Path prpDir) {
        var abs = prpDir.toAbsolutePath().normalize();
        return abs.getFileName() != null && "prp".equalsIgnoreCase(abs.getFileName().toString()) ? abs.getParent() : null;
    }

    public static boolean missing(Path project) {
        return !Files.isRegularFile(ProjFile.file(project));
    }

    /** Problems with the typed names; empty when fine. {@code all}: every name is required (a new proj.json). */
    public static List<String> check(ProjJson n, Function<String, String> owner, boolean all) {
        var errors = new ArrayList<String>();
        if (TabName.cleanKey(n.name()).isEmpty() && (all || n.name() != null))
            errors.add(SHORT + ": at least one letter or digit");
        var a3 = alias(ALIAS3, n.alias3(), 3, owner, all);
        var a4 = alias(ALIAS4, n.alias4(), 4, owner, all);
        if (a3 != null) errors.add(a3);
        if (a4 != null) errors.add(a4);
        if (all && blank(n.longName())) errors.add(FULL + ": cannot be blank");
        return errors;
    }

    /** The problem with one alias, or null. A blank one is a problem only when {@code required}. */
    public static String alias(String label, String raw, int length, Function<String, String> owner, boolean required) {
        var typed = raw == null ? "" : raw.strip();
        if (typed.isEmpty() && !required) return null;
        if (!typed.matches("[A-Za-z0-9]{" + length + "}")) return label + ": exactly " + length + " letters or digits";
        var other = owner.apply(typed.toLowerCase());
        return other == null ? null : label + ": " + typed.toLowerCase() + " is already the short name of " + other;
    }

    /** The names cleaned the way they are stored: lower case, no stray spaces. */
    public static ProjJson normalize(ProjJson n) {
        return new ProjJson().name(TabName.cleanKey(n.name()))
                .alias3(blank(n.alias3()) ? null : n.alias3().strip().toLowerCase())
                .alias4(blank(n.alias4()) ? null : n.alias4().strip().toLowerCase())
                .longName(blank(n.longName()) ? null : n.longName().strip());
    }

    /**
     * The gate of a project's first PRP. Does nothing when proj.json exists (or the folder is not a
     * project's prp folder); otherwise the names must be complete and valid, or this refuses.
     *
     * @return the normalized names to {@link #write} once the PRP exists, or null when none are needed
     * @throws IllegalArgumentException saying what to pass
     */
    public static ProjJson gate(Path prpDir, ProjJson typed) {
        var project = projectOf(prpDir);
        if (project == null || !missing(project)) return null;
        var given = typed == null ? new ProjJson() : typed;
        var errors = check(given, k -> PrpSessions.keyOwner(k, project), true);
        if (!errors.isEmpty()) throw new IllegalArgumentException(refusal(project, errors));
        return normalize(given);
    }

    /** Writes proj.json with a new uuid and the names {@link #gate} returned; no-op for null. */
    public static void write(Path prpDir, ProjJson names) {
        var project = projectOf(prpDir);
        if (names == null || project == null) return;
        ProjFile.save(project, names.uuid(ProjId.mint()));
        PrpSessions.cacheKey(project, names.name());
    }

    /** One line for a project whose proj.json has no aliases, else null. Never a refusal. */
    public static String hint(Path prpDir) {
        var project = projectOf(prpDir);
        var p = project == null ? null : ProjFile.read(project);
        if (p == null || !blank(p.alias3()) && !blank(p.alias4())) return null;
        var s = suggest(project);
        return "hint: prp/proj.json has no alias3/alias4; add them with: proj prp key --alias3 "
                + or(s.alias3(), "abc") + " --alias4 " + or(s.alias4(), "abcd");
    }

    /** Values derived from the folder name, to offer in the message or the form. */
    public static ProjJson suggest(Path project) {
        var folder = project.getFileName() == null ? "project" : project.getFileName().toString();
        var clean = TabName.cleanKey(String.join("", TabName.words(TabName.withoutDate(folder))));
        return new ProjJson().name(ProjFile.inferName(project)).longName(TabName.withoutDate(folder))
                .alias3(clean.length() >= 3 ? clean.substring(0, 3) : null)
                .alias4(clean.length() >= 4 ? clean.substring(0, 4) : null);
    }

    static String refusal(Path project, List<String> errors) {
        var s = suggest(project);
        var out = new StringBuilder("this project has no prp/proj.json, so its first PRP needs four names:\n")
                .append("  --short <name>       the short name, the key of tickets (billing in billing-1)\n")
                .append("  --alias3 <abc>       exactly 3 letters or digits\n")
                .append("  --alias4 <abcd>      exactly 4 letters or digits\n")
                .append("  --full-name <text>   the full name\n");
        for (var e : errors) out.append("  problem: ").append(e).append('\n');
        return out.append("for this folder, for example: --short ").append(s.name())
                .append(" --alias3 ").append(or(s.alias3(), "abc"))
                .append(" --alias4 ").append(or(s.alias4(), "abcd"))
                .append(" --full-name \"").append(s.longName()).append('"').toString();
    }

    static String or(String v, String placeholder) {
        return v == null ? placeholder : v;
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
