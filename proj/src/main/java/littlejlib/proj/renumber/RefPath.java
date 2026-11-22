package littlejlib.proj.renumber;

import java.util.Locale;
import java.util.Set;

/** The folder part of a path written just before a PRP file name, and the words that never name a project. */
final class RefPath {

    private RefPath() { }

    static final Set<String> STOP = Set.of("the", "a", "an", "this", "that", "these", "those", "of", "in", "see", "per", "our",
            "its", "and", "or", "from", "to", "for", "by", "on", "with", "at", "as", "is", "was", "were", "be", "under", "into",
            "after", "before", "than", "like", "same", "each", "every", "which", "whose", "my", "your", "their", "own", "also",
            "then", "now", "both", "earlier", "later", "current", "parent", "child", "sibling", "related", "closed", "open", "new",
            "old", "next", "previous", "last", "first", "supersedes", "superseded", "blocks", "via", "file", "files", "about",
            "until", "since", "when", "while", "if", "not", "but", "so", "do", "did", "done", "here", "there", "back", "-", "--");

    /**
     * The text of the path before {@code prp/} (forward slashes, possibly empty) when the name follows {@code .../prp/};
     * null when it follows no prp folder at all.
     */
    static String before(String text) {
        var norm = text.replace("\\\\", "/").replace('\\', '/');
        if (!norm.toLowerCase(Locale.ROOT).endsWith("prp/")) return null;
        var head = norm.length() - 4;
        if (head > 0 && norm.charAt(head - 1) != '/' && !delimiter(norm.charAt(head - 1))) return null;
        var start = head;
        while (start > 0 && !delimiter(norm.charAt(start - 1))) start--;
        if (start > 1 && norm.charAt(start - 1) == ':' && Character.isLetter(norm.charAt(start - 2))
                && (start == 2 || delimiter(norm.charAt(start - 3)))) start -= 2;
        return norm.substring(start, head);
    }

    static boolean delimiter(char c) {
        return " \t\"'`(<[=,@:*|>".indexOf(c) >= 0;
    }
}
