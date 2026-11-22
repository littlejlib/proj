package littlejlib.proj.track;

import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpNumber;
import littlejlib.proj.prp.PrpUuid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the one-line descriptions out of a hand-kept {@code 00-prp.index.md}. Three shapes exist:
 * {@code - 07 description -- STATUS}, {@code - A1-prp-name.md - (was 100) description}, and
 * {@code - 01-prp-name.md} followed by indented {@code - Scope: ...} sub-bullets.
 */
public final class IndexMigrate {

    private IndexMigrate() { }

    static final Pattern TOP = Pattern.compile("^[-*]\\s+`?(" + PrpNumber.LEGACY + "|(?=\\w{0,3}\\d)" + PrpNumber.CODE + ")((?:-prp-|00-)[^`\\s]*?\\.md)?`?(?=[\\s:.,]|$)(.*)$",
            Pattern.CASE_INSENSITIVE);

    static final Pattern SUB = Pattern.compile("^\\s+[-*]\\s+(.*)$");

    static final Pattern LABEL = Pattern.compile("^(\\w[\\w -]{0,20}):\\s*(.*)$");

    public static final String OLD = "00-prp.index.md", RENAMED = "00-prp.index.md.migrated";

    /** What a migration did: summaries written, PRPs it said nothing about, numbers it named that are not PRPs. */
    public record Report(int written, int kept, List<String> notInOld, List<String> notPrps, int lines, String preamble) { }

    /**
     * Each PRP of the folder that has no summary yet gets the old index's line as its summary; then the
     * old file is renamed so nobody keeps editing it. Its text stays intact in the renamed file.
     */
    public static Report apply(Path prpDir, boolean dryRun) throws IOException {
        var old = prpDir.resolve(OLD);
        var lines = Files.readAllLines(old, StandardCharsets.UTF_8);
        var found = summaries(lines);
        var written = 0;
        var kept = 0;
        var notInOld = new ArrayList<String>();
        var numbers = new HashSet<String>();
        for (var e : PrpCreator.list(prpDir)) {
            numbers.add(e.getIndex());
            var text = found.get(e.getIndex());
            if (text == null) {
                notInOld.add(e.getIndex());
                continue;
            }
            var meta = MetaFile.read(prpDir, e.getIndex());
            if (meta != null && meta.summary() != null) {
                kept++;
                continue;
            }
            written++;
            if (dryRun) continue;
            var uuid = PrpUuid.ensure(e.getMainFile(), true).uuid();
            MetaFile.update(prpDir, e.getIndex(), uuid, m -> m.summary(text));
        }
        var notPrps = found.keySet().stream().filter(n -> !numbers.contains(n)).toList();
        var preamble = Preamble.move(prpDir, Preamble.leading(lines), dryRun);
        if (!dryRun) Files.move(old, prpDir.resolve(RENAMED));
        return new Report(written, kept, notInOld, notPrps, lines.size(), preamble);
    }

    /** PRP number (upper case) to its description, first mention wins. */
    public static Map<String, String> summaries(List<String> lines) {
        var out = new LinkedHashMap<String, String>();
        for (var i = 0; i < lines.size(); i++) {
            var m = TOP.matcher(lines.get(i));
            if (!m.matches()) continue;
            var number = m.group(1).toUpperCase();
            var text = clean(m.group(3));
            if (text.isEmpty()) text = fromSubBullets(lines, i + 1);
            if (!text.isEmpty()) out.putIfAbsent(number, cut(text));
        }
        return out;
    }

    /** The {@code Scope:} sub-bullet under a block heading, else the first sub-bullet that is not a label of files. */
    static String fromSubBullets(List<String> lines, int from) {
        String first = null;
        for (var i = from; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.isBlank()) continue;
            var s = SUB.matcher(line);
            if (!s.matches()) break;
            var label = LABEL.matcher(s.group(1));
            if (label.matches() && label.group(1).equalsIgnoreCase("scope")) return clean(label.group(2));
            if (first == null && !(label.matches() && label.group(1).toLowerCase().contains("file")))
                first = clean(label.matches() ? label.group(2) : s.group(1));
        }
        return first == null ? "" : first;
    }

    static String clean(String s) {
        return s.replaceAll("^[\\s\\-–—:|]+", "").replaceAll("^\\(was [^)]*\\)\\s*", "").replace("`", "")
                .replaceAll("\\*\\*", "").strip();
    }

    static String cut(String s) {
        if (s.length() <= MetaFile.MAX_SUMMARY) return s;
        var at = s.lastIndexOf(' ', MetaFile.MAX_SUMMARY - 4);
        return s.substring(0, at > 100 ? at : MetaFile.MAX_SUMMARY - 4) + " ...";
    }
}
