package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The terminal tab's name: {@code <project>-<NN>-<task>}, as in {@code billing-38-r2}.
 * <p>
 * Neither half is derivable - {@code billing} appears nowhere in the path of the project it names,
 * and {@code r2} is one word out of a nine-word PRP name. So what is built here is a GUESS, offered
 * in an editable field; what the user types is remembered and wins from then on.
 */
public final class TabName {

    private TabName() { }

    static final int MAX_PART = 12;

    /** Words that begin a PRP name without saying what it is about. */
    static final Set<String> WEAK = Set.of("add", "adding", "improve", "improving", "improvements",
            "fix", "fixes", "fixing", "make", "making", "new", "update", "updating", "change",
            "changing", "the", "an", "and", "for", "of", "to", "in", "on", "with", "prp",
            "some", "more", "also", "option", "options", "support", "better", "show", "allow",
            "we", "you", "my", "this", "that", "it", "us", "need", "needs", "can", "should",
            "must", "even", "just", "set", "get", "use", "using", "please", "non");


    /** What to put in the field: what was remembered for this PRP, else a guess. */
    public static String of(Path prpFile, String remembered) {
        return remembered == null || remembered.isBlank() ? derive(prpFile, null) : remembered;
    }

    /**
     * @param projectShort the {@link ProjectKey} of this project, or null to guess one
     */
    public static String derive(Path prpFile, String projectShort) {
        var index = PrpPrompt.indexOf(prpFile);
        var key = keyOr(prpFile, projectShort);
        var task = taskOf(prpFile);
        return index == null ? key + "-" + task : ticket(key, shortIndex(prpFile.toAbsolutePath().getParent(), index)) + "-" + task;
    }

    /**
     * The ticket: {@code billing-38}. A tab name is this plus the task, so the identifier a person
     * says out loud is a prefix of the name they see on the tab.
     */
    public static String ticket(String key, String prpIndex) {
        return prpIndex == null || prpIndex.isBlank() ? key : key + "-" + prpIndex;
    }

    /** The ticket for a PRP, given the project's key or nothing. */
    public static String ticketOf(Path prpFile, String projectShort) {
        var index = PrpPrompt.indexOf(prpFile);
        return ticket(keyOr(prpFile, projectShort), index == null ? null : shortIndex(prpFile.toAbsolutePath().getParent(), index));
    }

    /** The number as names show it: leading zeros removed down to the width the folder's highest number needs. */
    public static String shortIndex(Path prpDir, String index) {
        if (index == null || PrpNumber.isLegacy(index)) return index;
        try {
            return PrpNumber.shortForm(index, PrpNumber.width(PrpCreator.highestIndex(prpDir)));
        } catch (IOException | RuntimeException e) {
            return index;
        }
    }

    static String keyOr(Path prpFile, String projectShort) {
        return projectShort == null || projectShort.isBlank()
                ? shortenProject(AgentLauncher.projectRootOf(prpFile)) : projectShort;
    }

    /** The project half of a name already typed, so the next PRP of the same project inherits it. */
    public static String projectPartOf(String tabName, String prpIndex) {
        if (tabName == null || prpIndex == null) return null;
        var forms = PrpNumber.isLegacy(prpIndex) ? List.of(prpIndex)
                : IntStream.rangeClosed(1, PrpNumber.WIDTH).mapToObj(w -> PrpNumber.shortForm(prpIndex, w)).distinct().toList();
        for (var f : forms) {
            var at = tabName.indexOf("-" + f + "-");
            if (at > 0) return tabName.substring(0, at);
        }
        return null;
    }

    /**
     * A key to offer when none is set: the folder's name without its date and filler words, its first
     * word plus the second when both fit in {@link #MAX_PART}. {@code 2026-02-25 THE Acme Bio} gives {@code acmebio}.
     */
    public static String shortenProject(Path projectDir) {
        var name = projectDir == null || projectDir.getFileName() == null
                ? "proj" : projectDir.getFileName().toString();
        if (name.strip().matches(DATE)) return cut(clean(name));
        var w = words(withoutDate(name));
        var key = w.isEmpty() ? "" : w.size() > 1 && w.get(0).length() + w.get(1).length() <= MAX_PART ? w.get(0) + w.get(1) : w.get(0);
        var out = key.isEmpty() ? clean(name) : key;
        return out.isEmpty() ? "proj" : cut(out);
    }

    static final String DATE = "\\d{4}(?:-\\d{2}){0,2}(?:\\s+to\\s+\\d{4}(?:-\\d{2}){0,2})?";

    /** A folder name with a date at its start or end removed: {@code 2026-02-25 THE Acme Bio} gives {@code THE Acme Bio}. */
    public static String withoutDate(String name) {
        var s = name.strip().replaceFirst("^" + DATE + "[\\s_.-]*", "").replaceFirst("[\\s_.-]*" + DATE + "$", "").strip();
        return s.isEmpty() ? name.strip() : s;
    }

    /** The meaningful words of a name, lower case, letters and digits only, filler words left out. */
    static List<String> words(String name) {
        var all = List.of(name.toLowerCase().split("[^a-z0-9]+")).stream().filter(w -> !w.isEmpty()).toList();
        var strong = all.stream().filter(w -> !WEAK.contains(w) && !w.equals("a")).toList();
        return strong.isEmpty() ? all : strong;
    }

    /** The first word of the PRP's name that carries meaning. */
    static String taskOf(Path prpFile) {
        var shortName = PrpHeader.shortNameOf(prpFile);
        if (shortName != null) return shortName;
        var file = prpFile.getFileName() == null ? "" : prpFile.getFileName().toString();
        var m = PrpFiles.mainMatcher(file);
        var name = m.matches() ? m.group(2) : file.replaceAll("\\.md$", "");
        var words = name.toLowerCase().split("[^a-z0-9]+");
        for (var w : words) if (w.length() > 1 && !WEAK.contains(w)) return cut(w);
        for (var w : words) if (!w.isEmpty()) return cut(w);
        return "task";
    }

    static String clean(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** One word, the way the tab names on this machine are written - lower case, no separators. */
    public static String cleanKey(String raw) {
        return raw == null ? "" : clean(raw.trim());
    }

    static String cut(String s) {
        return s.length() <= MAX_PART ? s : s.substring(0, MAX_PART);
    }
}
