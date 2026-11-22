package littlejlib.proj.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a report covers, the same for the URL and the command line: {@code with}, {@code follow}, {@code prp},
 * {@code goal}, {@code tag}, {@code done}, {@code panel}, {@code maxAge}. Items are written {@code Project:id} in a URL
 * ({@code #} starts a fragment there) and {@code Project#id} anywhere else; both are read.
 */
public record ReportQuery(List<String> with, boolean follow, List<String> prps, String goal, List<String> tags,
                          boolean done, List<String> panels, Integer maxAgeMinutes) {

    public static ReportQuery of(Map<String, String> params) {
        return new ReportQuery(list(params.get("with")), !"0".equals(params.get("follow")), list(params.get("prp")),
                blank(params.get("goal")), list(params.get("tag")), "1".equals(params.get("done")), list(params.get("panel")),
                params.get("maxAge") == null ? null : Integer.valueOf(params.get("maxAge")));
    }

    /** The query as URL parameters, for links that keep the scope. */
    public Map<String, String> params() {
        var out = new LinkedHashMap<String, String>();
        if (!with.isEmpty()) out.put("with", String.join(",", with));
        if (!follow) out.put("follow", "0");
        if (!prps.isEmpty()) out.put("prp", String.join(",", prps));
        if (goal != null) out.put("goal", goal.replace('#', ':'));
        if (!tags.isEmpty()) out.put("tag", String.join(",", tags));
        if (done) out.put("done", "1");
        if (!panels.isEmpty()) out.put("panel", String.join(",", panels));
        return out;
    }

    public ReportQuery withDone(boolean d) {
        return new ReportQuery(with, follow, prps, goal, tags, d, panels, maxAgeMinutes);
    }

    static List<String> list(String s) {
        var out = new ArrayList<String>();
        if (s != null) for (var p : s.split("[,\\s]+")) if (!p.isBlank()) out.add(p.strip());
        return out;
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** {@code billing:31} or {@code billing#31} as [project, id]; null when it is not that shape. */
    public static String[] itemRef(String s) {
        if (s == null) return null;
        var m = java.util.regex.Pattern.compile("^(.+)[#:](\\d+)$").matcher(s.strip());
        return m.matches() ? new String[]{m.group(1), m.group(2)} : null;
    }
}
