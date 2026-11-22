package littlejlib.proj.track;

import littlejlib.proj.prp.PrpFiles;
import littlejlib.proj.prp.PrpNumber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Old numbers of renumbered PRPs, from each PRP's meta.json {@code was}: old number to current number. */
public final class PrpWas {

    private PrpWas() { }

    public static Map<String, String> map(Path prpDir) {
        var out = new HashMap<String, String>();
        if (prpDir == null || !Files.isDirectory(prpDir)) return out;
        try (var files = Files.list(prpDir)) {
            for (var f : files.toList()) {
                var m = PrpFiles.DATA.matcher(f.getFileName().toString());
                if (!m.matches() || !m.group(2).equals("meta.json")) continue;
                var meta = MetaFile.read(prpDir, m.group(1));
                if (meta != null && meta.was() != null && !meta.was().isBlank()) out.put(meta.was().toUpperCase(), m.group(1));
            }
        } catch (IOException e) {
            // unreadable folder: no old numbers known
        }
        return out;
    }

    /** The current number for a number as written: a legacy number of a renumbered PRP becomes its new one. */
    public static String current(Path prpDir, String number) {
        if (number == null || !PrpNumber.isLegacy(number)) return number;
        return map(prpDir).getOrDefault(number.toUpperCase(), number);
    }
}
