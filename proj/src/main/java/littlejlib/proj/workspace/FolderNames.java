package littlejlib.proj.workspace;

import java.util.regex.Pattern;

/**
 * The SECOND way a folder in a plain group says it is a piece of work: a date in its name.
 */
public final class FolderNames {

    /**
     * A year, then optionally a month, then optionally a day.
     */
    static final String DATE = "\\d{4}([-_.]\\d{1,2}([-_.]\\d{1,2})?)?";

    static final Pattern LEADING = Pattern.compile("^" + DATE + "(\\b|_).*");
    static final Pattern TRAILING = Pattern.compile(".*(\\b|_)" + DATE + "$");

    private FolderNames() { }

    public static boolean dated(String name) {
        if (name == null || name.isBlank()) return false;
        var n = name.trim();
        return LEADING.matcher(n).matches() || TRAILING.matcher(n).matches();
    }
}
