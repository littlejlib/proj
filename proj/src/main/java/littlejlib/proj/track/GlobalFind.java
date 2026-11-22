package littlejlib.proj.track;

import littlejlib.proj.prp.Base34;
import littlejlib.proj.prp.PrpNumber;
import littlejlib.proj.prp.PrpUuid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Matching a query against the machine-wide cache. A uuid (or a start of one, 6 symbols or more)
 * finds that PRP wherever it now lives; {@code key-NN} or a bare number finds by ticket; anything
 * else is words, all of which must appear in the project, name, summary or file name.
 */
public final class GlobalFind {

    private GlobalFind() { }

    public static final int MIN_UUID_PREFIX = 6;

    /** A bare number needs a digit ({@code 7}, {@code 001M}); after a key any 1-4 symbols ({@code acme-1M}, {@code acme-zz}). Words like "db" are words. */
    static final Pattern TICKET = Pattern.compile("(?i)(?:[\\w.]+-[0-9a-z]{1,4}|(?=[a-z]{0,3}\\d)[0-9a-z]{1,4})"),
            WAS = Pattern.compile("(?i)was:(?:([\\w.]+)-)?([0-9a-z]{1,2})");

    public static List<GlobalLine> find(List<GlobalLine> all, String query) {
        var q = query.strip();
        var was = WAS.matcher(q);
        if (was.matches()) return sorted(was(all, was.group(1), was.group(2)));
        var hits = uuid(all, q);
        if (hits.isEmpty()) hits = TICKET.matcher(q).matches() ? ticket(all, q) : words(all, q);
        return sorted(hits);
    }

    static List<GlobalLine> sorted(List<GlobalLine> hits) {
        return hits.stream().sorted(Comparator.comparing((GlobalLine l) -> String.valueOf(l.updated())).reversed()).toList();
    }

    /** Only a hit counts: o and i read as 0 and 1, so almost any word is a valid start of an id. */
    static List<GlobalLine> uuid(List<GlobalLine> all, String q) {
        if (q.contains(" ") || q.length() < MIN_UUID_PREFIX || !Base34.validPrefix(q)) return List.of();
        var p = Base34.normalize(q);
        return all.stream().filter(l -> l.uuid() != null && !l.uuid().isEmpty() && l.uuid().startsWith(p)).toList();
    }

    static List<GlobalLine> ticket(List<GlobalLine> all, String q) {
        var dash = q.lastIndexOf('-');
        var key = dash < 0 ? null : q.substring(0, dash).toLowerCase(Locale.ROOT);
        var typed = dash < 0 ? q : q.substring(dash + 1);
        var hits = all.stream().filter(l -> PrpNumber.same(typed, l.number()) && keyed(l, key)).toList();
        return hits.isEmpty() && key != null ? words(all, q) : hits;
    }

    static boolean keyed(GlobalLine l, String key) {
        return key == null || ofProject(l, key);
    }

    /** {@code was:55}: a renumbered PRP by its old number. */
    static List<GlobalLine> was(List<GlobalLine> all, String key, String old) {
        String number;
        try {
            number = PrpNumber.parseLegacy(old);
        } catch (NumberFormatException e) {
            return List.of();
        }
        var k = key == null ? null : key.toLowerCase(Locale.ROOT);
        return all.stream().filter(l -> number.equalsIgnoreCase(l.was()) && keyed(l, k)).toList();
    }

    /** The project's short name or either alias, or the end of its folder path. */
    static boolean ofProject(GlobalLine l, String key) {
        return key.equalsIgnoreCase(l.key()) || key.equalsIgnoreCase(l.alias3()) || key.equalsIgnoreCase(l.alias4())
                || l.project().toLowerCase(Locale.ROOT).endsWith(key);
    }

    static List<GlobalLine> words(List<GlobalLine> all, String q) {
        var words = Arrays.stream(q.toLowerCase(Locale.ROOT).split("\\s+")).filter(w -> !w.isBlank()).toList();
        return all.stream().filter(l -> {
            var hay = (l.project() + " " + l.key() + " " + l.alias3() + " " + l.alias4() + " " + l.name() + " " + l.summary() + " " + Path.of(l.file()).getFileName())
                    .toLowerCase(Locale.ROOT).replace('_', ' ');
            return words.stream().allMatch(w -> hay.contains(w.replace('_', ' ')));
        }).toList();
    }

    /** True when the cached location still holds that PRP: the file exists and carries the uuid. */
    public static boolean stillThere(GlobalLine l) {
        var f = Path.of(l.file());
        return Files.isRegularFile(f) && (l.uuid() == null || l.uuid().isEmpty() || l.uuid().equals(PrpUuid.read(f)));
    }
}
