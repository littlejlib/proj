package littlejlib.proj.prp;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

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

    static final Pattern PRP_FILE = Pattern.compile("^(\\d{2})-prp-(.+?)(\\.closed)?\\.md$",
            Pattern.CASE_INSENSITIVE);

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
        return index == null ? key + "-" + task : ticket(key, index) + "-" + task;
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
        return ticket(keyOr(prpFile, projectShort), PrpPrompt.indexOf(prpFile));
    }

    static String keyOr(Path prpFile, String projectShort) {
        return projectShort == null || projectShort.isBlank()
                ? shortenProject(AgentLauncher.projectRootOf(prpFile)) : projectShort;
    }

    /** The project half of a name already typed, so the next PRP of the same project inherits it. */
    public static String projectPartOf(String tabName, String prpIndex) {
        if (tabName == null || prpIndex == null) return null;
        var marker = "-" + prpIndex + "-";
        var at = tabName.indexOf(marker);
        return at <= 0 ? null : tabName.substring(0, at);
    }

    /** A key to offer when none is set: the folder's own name, cleaned up. */
    public static String shortenProject(Path projectDir) {
        var name = projectDir == null || projectDir.getFileName() == null
                ? "proj" : projectDir.getFileName().toString();
        var clean = clean(name);
        return clean.isEmpty() ? "proj" : cut(clean);
    }

    /** The first word of the PRP's name that carries meaning. */
    static String taskOf(Path prpFile) {
        var file = prpFile.getFileName() == null ? "" : prpFile.getFileName().toString();
        var m = PRP_FILE.matcher(file);
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
