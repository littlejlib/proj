package littlejlib.proj.prp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The head of a PRP main file (PRP 001R). Written as YAML front matter:
 * <pre>
 * ---
 * prp_number: 1
 * prp_number_b34str: "0001"
 * prp_name: export
 * prp_3_letter_name: exp
 * prp_short_name: export
 * prp_uuid: 0al4a9rafajvsplsdzg5wyfeqx
 * prp_status: in_progress
 * created_utc: "2026-11-22T06:35:00Z"
 * last_worked_on_utc: "2026-11-22T09:10:00Z"
 * conventions: before anything else, run `proj prp guide` ...
 * ---
 * </pre>
 * Read in that form or in the older one: {@code - PRP Number:}, {@code - PRP Name:}, {@code - PRP UUID:},
 * {@code - Conventions:} lines and the old {@code - Usage Guide:} block with its indented lines, which
 * {@code proj prp guide} replaced and the upgrade drops.
 */
public final class PrpHeader {

    public static final String CONVENTIONS = "before anything else, run `proj prp guide` and follow what it prints. It is the current PRP (project requirements prompt) convention and is managed by the proj tool. If proj is not installed and not found in path refer https://littlejlib.github.io/proj/ for download and install.";

    /** {@code prp_number} is the plain value of the base34 number ({@code 0001} is 1, {@code 001Q} is 58); {@code prp_number_b34str} the 4 symbols. */
    public static final List<String> KEYS = List.of("prp_number", "prp_number_b34str", "prp_name", "prp_3_letter_name", "prp_short_name", "prp_uuid",
            "prp_status", "created_utc", "last_worked_on_utc", "conventions");

    public String number, name, threeLetters, shortName, uuid, conventions;
    /** {@code prp_status} as written (see {@link PrpState}), and the two times, quoted UTC like {@code "2026-11-22T06:35:00Z"}; null when the key is absent. */
    public String status, createdUtc, lastWorkedOnUtc;
    /** Keys of a YAML head that proj does not know, kept as they were. */
    public final Map<String, String> extra = new LinkedHashMap<>();
    /** The line where the body starts. */
    public int bodyStart;
    public boolean yaml;
    /** The old Usage Guide block was present (dropped on upgrade). */
    public boolean usageGuide;

    static final Pattern OLD = Pattern.compile("^- (PRP Number|PRP Name|PRP UUID|Conventions|Usage Guide):[ \\t]*(.*?)[ \\t]*$");
    static final Pattern YAML_LINE = Pattern.compile("^([A-Za-z0-9_]+)[ \\t]*:[ \\t]?(.*)$");

    /** The head of a file, read from its first lines only; an empty header when it cannot be read. */
    public static PrpHeader of(java.nio.file.Path file) {
        try (var lines = java.nio.file.Files.lines(file, java.nio.charset.StandardCharsets.UTF_8)) {
            return parse(lines.limit(80).toList());
        } catch (java.io.IOException | RuntimeException e) {
            return new PrpHeader();
        }
    }

    /** The state in {@code prp_status}, or null when the key is absent or holds something outside the set. */
    public PrpState state() {
        return PrpState.parse(status);
    }

    /** {@code prp_short_name} of a PRP file (what its terminal tab is named after), or null. */
    public static String shortNameOf(java.nio.file.Path file) {
        var s = of(file).shortName;
        return s == null || !s.matches("[a-z0-9][a-z0-9-]*") ? null : s;
    }

    public static PrpHeader parse(List<String> lines) {
        var h = new PrpHeader();
        if (!lines.isEmpty() && lines.get(0).strip().equals("---")) {
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).strip().equals("---")) {
                    h.yaml = true;
                    h.bodyStart = i + 1;
                    return h;
                }
                var m = YAML_LINE.matcher(lines.get(i));
                if (m.matches()) h.put(m.group(1), unquote(m.group(2).strip()));
            }
            return new PrpHeader();          // no closing ---: not front matter
        }
        int i = 0;
        boolean inGuide = false;
        for (; i < lines.size(); i++) {
            var line = lines.get(i);
            var m = OLD.matcher(line);
            if (m.matches()) {
                inGuide = m.group(1).equals("Usage Guide");
                switch (m.group(1)) {
                    case "PRP Number" -> h.number = m.group(2);
                    case "PRP Name" -> h.name = m.group(2);
                    case "PRP UUID" -> h.uuid = m.group(2);
                    case "Conventions" -> h.conventions = m.group(2);
                    default -> h.usageGuide = true;
                }
                continue;
            }
            if (inGuide && (line.startsWith("\t") || line.startsWith("  "))) continue;
            if (line.isBlank() && i < 60) {
                inGuide = false;
                continue;
            }
            break;
        }
        h.bodyStart = i;
        return h;
    }

    void put(String key, String value) {
        switch (key) {
            case "prp_number_b34str" -> number = value;
            case "prp_number" -> { if (number == null && !value.matches("\\d+")) number = value; }   // a b34 string from the first drafts
            case "prp_name" -> name = value;
            case "prp_3_letter_name" -> threeLetters = value;
            case "prp_short_name" -> shortName = value;
            case "prp_uuid" -> uuid = value;
            case "prp_status" -> status = value;
            case "created_utc" -> createdUtc = value;
            case "last_worked_on_utc" -> lastWorkedOnUtc = value;
            case "conventions" -> conventions = value;
            default -> extra.put(key, value);
        }
    }

    /** The YAML head, closing {@code ---} included, then one blank line. */
    public List<String> render() {
        var out = new ArrayList<String>();
        out.add("---");
        Integer value = null;
        try {
            if (number != null && PrpNumber.valid(number)) value = PrpNumber.value(number);
        } catch (RuntimeException ignored) {
            // a number that is not base34 keeps only its string form
        }
        if (value != null) out.add("prp_number: " + value);
        out.add("prp_number_b34str: " + quote(number, true));
        out.add("prp_name: " + quote(name, false));
        if (threeLetters != null) out.add("prp_3_letter_name: " + quote(threeLetters, false));
        if (shortName != null) out.add("prp_short_name: " + quote(shortName, false));
        if (uuid != null) out.add("prp_uuid: " + quote(uuid, false));
        if (status != null) out.add("prp_status: " + quote(status, false));
        if (createdUtc != null) out.add("created_utc: " + quote(createdUtc, true));
        if (lastWorkedOnUtc != null) out.add("last_worked_on_utc: " + quote(lastWorkedOnUtc, true));
        out.add("conventions: " + quote(conventions == null ? CONVENTIONS : conventions, false));
        extra.forEach((k, v) -> out.add(k + ": " + quote(v, false)));
        out.add("---");
        out.add("");
        return out;
    }

    /** Quoted when YAML would read it as something else: a number, a boolean, or text with ": ", " #", a leading symbol. */
    static String quote(String v, boolean always) {
        if (v == null) return "\"\"";
        var plain = !always && !v.isEmpty() && !v.matches("^[\\s\\-?:,\\[\\]{}#&*!|>'\"%@`].*") && !v.contains(": ") && !v.contains(" #")
                && !v.endsWith(":") && !v.matches("(?i)true|false|yes|no|on|off|null|~|[-+]?[0-9][0-9._]*") && v.equals(v.strip());
        return plain ? v : "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String unquote(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) return v.substring(1, v.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) return v.substring(1, v.length() - 1).replace("''", "'");
        return v;
    }
}
