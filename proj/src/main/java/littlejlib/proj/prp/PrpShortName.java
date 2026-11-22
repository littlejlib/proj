package littlejlib.proj.prp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A PRP's short name (for tabs: {@code <project>-<number>-<short name>}) and its 3-letter name (unique in its project),
 * derived from the PRP name by rule (PRP 001R). Draft rule, under review by the user.
 */
public final class PrpShortName {

    private PrpShortName() { }

    static final Set<String> FILLER = Set.of("a", "an", "the", "to", "of", "for", "and", "or", "in", "on", "at", "with", "by", "as",
            "from", "into", "onto", "via", "per", "is", "are", "be", "it", "its", "that", "this", "these", "those", "so", "than",
            "then", "when", "while", "only", "every", "each", "all", "any", "one", "no", "not", "unless", "like", "same", "new",
            "also", "more", "most", "very", "just", "do", "does", "make", "makes", "use", "using", "used", "should", "can", "will",
            "own", "itself", "our", "my", "his", "their", "who", "what", "which", "how", "why", "where", "there", "here");

    /** Verbs that open many names and say little ("add support for ..."): dropped when more words follow. */
    static final Set<String> OPENERS = Set.of("add", "adds", "added", "support", "implement", "create", "fix", "fixes", "make",
            "improve", "update", "handle", "allow", "enable", "build", "do", "get", "set", "move", "write", "proper", "better", "basic");

    /** Dates and "prp4" say nothing about the work; small numbers ("part 7", "q12") tell siblings apart, so they stay. */
    static final Pattern DATE = Pattern.compile("\\d{4}(\\d{2}){0,2}|prp\\d*");

    public record Names(String shortName, String threeLetters) { }

    /** Content words of a PRP name, in order, without filler, dates and the project's own words. */
    static List<String> words(String prpName, String project) {
        var name = prpName.toLowerCase(Locale.ROOT);
        var cut = name.indexOf(" - ");
        if (cut < 0) cut = name.indexOf("_-_");
        if (cut > 0) name = name.substring(0, cut);
        name = name.replaceAll("\\d{4}[-_]\\d{2}([-_]\\d{2})?", " ");
        var own = new HashSet<String>();
        if (project != null) for (var w : project.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) if (w.length() > 2) own.add(w);
        var out = new ArrayList<String>();
        for (var w : name.split("[^a-z0-9]+")) {
            if (w.isEmpty() || FILLER.contains(w) || DATE.matcher(w).matches() || own.contains(w)) continue;
            out.add(w);
        }
        while (out.size() > 1 && OPENERS.contains(out.get(0))) out.remove(0);
        if (out.isEmpty()) for (var w : name.split("[^a-z0-9]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /**
     * Drops a leading word that is the project's name or an abbreviation of it ("gate-rw" for "billing-gate-rw" in
     * project billing). {@code keep}: short forms that stay at the front anyway (the user's sub-module names).
     */
    public static String dropProjectPrefix(String shortName, String project, Set<String> keep) {
        if (project == null) return shortName;
        var p = project.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        var dash = shortName.indexOf('-');
        if (dash <= 0 || p.isEmpty()) return shortName;
        var first = shortName.substring(0, dash);
        if (keep.contains(first)) return shortName;
        var abbrev = first.length() <= 4 && first.charAt(0) == p.charAt(0) && subsequence(first, p);
        return p.equals(first) || p.startsWith(first) || abbrev ? shortName.substring(dash + 1) : shortName;
    }

    static boolean subsequence(String t, String p) {
        int j = 0;
        for (int i = 0; i < p.length() && j < t.length(); i++) if (p.charAt(i) == t.charAt(j)) j++;
        return j == t.length();
    }

    public static String shortName(String prpName, String project) {
        return shortName(words(prpName, project), 3, 24);
    }

    static String shortName(List<String> w, int maxWords, int maxChars) {
        var picked = new ArrayList<String>();
        for (var x : w) {
            if (picked.size() == maxWords) break;
            var next = String.join("-", picked) + (picked.isEmpty() ? "" : "-") + x;
            if (next.length() > maxChars && !picked.isEmpty()) break;
            picked.add(x.length() > 14 ? x.substring(0, 14) : x);
        }
        return String.join("-", picked);
    }

    /** Unique in the project: more words first ("revamp-part-4-q6"), then a number. */
    static String uniqueShortName(List<String> w, Set<String> takenShort) {
        for (int words = 3; words <= 6; words++) {
            var s = shortName(w, words, 12 + words * 6);
            if (!takenShort.contains(s)) return s;
        }
        var base = shortName(w, 3, 24);
        for (int i = 2; ; i++) if (!takenShort.contains(base + "-" + i)) return base + "-" + i;
    }

    /** Candidates for the 3-letter name, best first. */
    static List<String> threeCandidates(List<String> w) {
        var out = new LinkedHashSet<String>();
        var letters = w.stream().map(x -> x.replaceAll("[^a-z]", "")).filter(x -> !x.isEmpty()).toList();
        if (letters.isEmpty()) return List.of("prp");
        if (letters.size() >= 3) out.add("" + letters.get(0).charAt(0) + letters.get(1).charAt(0) + letters.get(2).charAt(0));
        var first = letters.get(0);
        if (first.length() >= 3) out.add(first.substring(0, 3));
        var cons = first.charAt(0) + first.substring(1).replaceAll("[aeiou]", "");
        if (cons.length() >= 3) out.add(cons.substring(0, 3));
        if (letters.size() >= 2) {
            var second = letters.get(1);
            if (first.length() >= 2) out.add(first.substring(0, 2) + second.charAt(0));
            if (second.length() >= 2) out.add(first.charAt(0) + second.substring(0, 2));
            if (second.length() >= 3) out.add(second.substring(0, 3));
        }
        for (int i = 2; i < first.length(); i++) out.add("" + first.charAt(0) + first.charAt(1) + first.charAt(i));
        var all = String.join("", letters);
        if (all.length() >= 3) out.add(all.substring(0, 3));
        if (out.isEmpty()) out.add((all + "prp").substring(0, 3));
        return new ArrayList<>(out);
    }

    /** {@code taken}: 3-letter names already used in the project; the chosen one is added to it. */
    public static Names derive(String prpName, String project, Set<String> taken) {
        var w = words(prpName, project);
        String three = null;
        for (var c : threeCandidates(w)) if (c.length() == 3 && !taken.contains(c)) {
            three = c;
            break;
        }
        if (three == null) {
            var base = threeCandidates(w).get(0).substring(0, Math.min(2, threeCandidates(w).get(0).length()));
            for (char d = '2'; d <= '9' && three == null; d++) if (!taken.contains(base + d)) three = base + d;
        }
        taken.add(three);
        var shortTaken = SHORT_TAKEN.computeIfAbsent(taken, k -> new HashSet<>());
        var s = uniqueShortName(w, shortTaken);
        shortTaken.add(s);
        return new Names(s, three);
    }

    /** Short names used per project, keyed by that project's set of 3-letter names (one set per project). */
    static final Map<Set<String>, Set<String>> SHORT_TAKEN = new java.util.IdentityHashMap<>();

    /** stdin: project TAB number TAB name (in number order per project); stdout: the same plus short name and 3 letters. */
    public static void main(String[] args) throws Exception {
        var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Map<String, Set<String>> taken = new HashMap<>();
        var out = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);
        for (String line; (line = in.readLine()) != null; ) {
            var f = line.split("\t", -1);
            if (f.length < 3) continue;
            var n = derive(f[2], f[0], taken.computeIfAbsent(f[0], k -> new HashSet<>()));
            out.println(line + "\t" + n.shortName() + "\t" + n.threeLetters());
        }
    }
}
