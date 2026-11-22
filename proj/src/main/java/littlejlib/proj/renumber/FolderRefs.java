package littlejlib.proj.renumber;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * One prp folder's mapping as references see it: old names to new, old numbers to new, and per old number the old
 * sub-file serial to its new slot. {@code unchanged} is a folder that keeps its names (refused this run).
 */
final class FolderRefs {

    static final Pattern SERIAL = Pattern.compile("^[0-9A-Z]{2}-prp\\.(\\d+)\\.");

    final Path dir;
    final Set<String> self;
    final boolean unchanged;
    final Map<String, String> names = new HashMap<>(), numbers = new HashMap<>();
    /** Old number to old serial to new slot; a serial shared by several files maps to null (ambiguous). */
    final Map<String, Map<String, String>> slots = new HashMap<>();
    final Pattern exact;

    FolderRefs(Path dir, Set<String> self, List<RenumberFile> files, boolean unchanged) {
        this.dir = dir.toAbsolutePath().normalize();
        this.self = self;
        this.unchanged = unchanged;
        for (var f : files) {
            names.put(f.oldFile(), f.newFile());
            numbers.put(f.oldNumber(), f.newNumber());
            if ("main".equals(f.kind()) && f.oldFile().endsWith(".closed.md"))
                names.putIfAbsent(f.oldFile().replace(".closed.md", ".md"), f.newFile());
            var m = SERIAL.matcher(f.oldFile());
            if (("sub".equals(f.kind()) || "dir".equals(f.kind())) && m.find()) {
                var bySerial = slots.computeIfAbsent(f.oldNumber(), k -> new HashMap<>());
                var slot = f.newFile().substring(4, 6);
                var serial = String.valueOf(Integer.parseInt(m.group(1)));
                bySerial.put(serial, bySerial.containsKey(serial) ? null : slot);
            }
        }
        var alternation = names.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b).orElse("(?!)");
        exact = Pattern.compile("(?:" + alternation + ")(?![A-Za-z0-9_])");
    }

    /** The new slot of an old sub-file serial, or null when unknown or shared. */
    String slot(String oldNumber, String serial) {
        var bySerial = slots.get(oldNumber);
        return bySerial == null ? null : bySerial.get(String.valueOf(Integer.parseInt(serial)));
    }
}
