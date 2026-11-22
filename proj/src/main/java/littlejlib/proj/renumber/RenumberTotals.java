package littlejlib.proj.renumber;

import java.util.*;
import java.util.stream.Collectors;

/** The size of a run at a glance: folders, renames, rewritten files, review items by reason. */
public final class RenumberTotals {

    private RenumberTotals() { }

    /** The reason of a review item without its particulars (numbers, the word before, the matched text). */
    public static String reason(String why) {
        var r = why.replaceAll(": [^:]*$", "");
        if (r.startsWith("after '")) return "may be another project's PRP";
        return r.replaceAll("PRP [0-9A-Z]{2}", "PRP NN");
    }

    public static List<String> lines(List<RenumberResult> results, boolean dryRun) {
        var count = results.stream().collect(Collectors.groupingBy(RenumberResult::state, TreeMap::new, Collectors.counting()));
        var renames = results.stream().filter(r -> r.plan() != null && !r.state().equals("refused")).mapToInt(r -> r.plan().files().size()).sum();
        var rewritten = results.stream().mapToInt(r -> r.rewritten().size()).sum();
        var quiet = results.stream().mapToInt(RenumberResult::quiet).sum();
        var reasons = results.stream().flatMap(r -> r.reviews().stream()).collect(Collectors.groupingBy(v -> reason(v.why()), Collectors.counting()));
        var reviews = reasons.values().stream().mapToLong(Long::longValue).sum();
        var out = new ArrayList<String>();
        out.add("totals: " + results.size() + " folders (" + count.entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(", ")) + "), " + renames + " renames, " + rewritten + " files " + (dryRun ? "to rewrite" : "rewritten")
                + ", " + reviews + " review items, " + quiet + " unresolved references inside logs left as they are" + (dryRun ? " (dry run: nothing changed)" : ""));
        reasons.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> out.add("  review  " + e.getValue() + "  " + e.getKey()));
        return out;
    }
}
