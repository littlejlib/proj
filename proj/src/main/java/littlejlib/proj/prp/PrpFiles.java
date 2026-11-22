package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static littlejlib.proj.prp.PrpNumber.*;

/**
 * The file names of a PRP numbered NNNN: {@code NNNN00-name.md} (slot 00, the PRP), {@code NNNN01.status.jsonl},
 * {@code NNNN01.meta.json}, {@code NNNN01.status.md} (slot 01, its data), {@code NNNNSS-what.ext} (sub-files,
 * slots 02..ZY) and {@code NNNNZZ-SS.logs.ext} (logs). Legacy names ({@code NN-prp-name.md}, {@code NN-prp.status.md},
 * {@code NN-prp.02.notes.md}, {@code NN-prp.99.01.logs.md}) stay readable; a builder writes the scheme of the index it is given.
 */
public final class PrpFiles {

    private PrpFiles() { }

    static final String N = "(" + CODE + ")", S = SYMBOL + "{2}", L = "(" + LEGACY + ")";
    public static final String MAIN_SLOT = "00", DATA_SLOT = "01", LOG_SLOT = "ZZ";

    public static final Pattern
            NEW_FILE = Pattern.compile("^" + SYMBOL + "{6}[-.]"),
            MAIN = Pattern.compile("^" + N + "00-(.+?)(\\.closed)?\\.md$"),
            DATA = Pattern.compile("^" + N + "01\\.(status\\.md|status\\.jsonl|meta\\.json)$"),
            LOG = Pattern.compile("^" + N + "ZZ-(" + S + ")\\.logs\\.(txt|md|jsonl)$"),
            SUB = Pattern.compile("^" + N + "(" + S + ")-(.+)$"),
            STRICT = Pattern.compile("^" + N + "(?:00-|01\\.|ZZ-|" + S + "-)"),
            OLD_ANY = Pattern.compile("^" + L + "-prp[-.].+$"),
            OLD_MAIN = Pattern.compile("^" + L + "-prp-(.+?)(\\.closed)?\\.md$"),
            OLD_DATA = Pattern.compile("^" + L + "-prp\\.(status\\.md|status\\.jsonl|meta\\.json)$"),
            OLD_LOG = Pattern.compile("^" + L + "-prp\\.99\\.(\\d+)\\.logs\\.(txt|md|jsonl)$", Pattern.CASE_INSENSITIVE),
            OLD_SUB = Pattern.compile("^" + L + "-prp\\.(.+)$");

    /** Project files that look like PRP 00 in the legacy scheme but are not a PRP. */
    public static boolean projectFile(String name) {
        return name.startsWith("00-prp.index.");
    }

    /** A new-scheme PRP file: the 6-symbol prefix, then {@code -} (or {@code .} only in slot 01), so README.md is not one. */
    public static boolean isNew(String name) {
        return NEW_FILE.matcher(name).find() && STRICT.matcher(name).find();
    }

    public static boolean isLegacy(String name) {
        return !projectFile(name) && OLD_ANY.matcher(name).matches();
    }

    /** The PRP number of any PRP file in either scheme, or null. */
    public static String numberOf(String name) {
        if (isNew(name)) return name.substring(0, WIDTH);
        if (!isLegacy(name)) return null;
        var m = OLD_ANY.matcher(name);
        return m.matches() ? m.group(1) : null;
    }

    /** A sub-file slot: neither the PRP (00), its data (01) nor its logs (ZZ). */
    public static boolean subSlot(String slot) {
        return !slot.equals(MAIN_SLOT) && !slot.equals(DATA_SLOT) && !slot.equals(LOG_SLOT);
    }

    public static Matcher mainMatcher(String name) {
        var m = MAIN.matcher(name);
        return m.matches() ? m : OLD_MAIN.matcher(name);
    }

    public static String main(String index, String slug, boolean closed) {
        var tail = slug + (closed ? ".closed" : "") + ".md";
        return PrpNumber.isLegacy(index) ? index + "-prp-" + tail : index + MAIN_SLOT + "-" + tail;
    }

    static Path data(Path prpDir, String index, String what) {
        return prpDir.resolve(PrpNumber.isLegacy(index) ? index + "-prp." + what : index + DATA_SLOT + "." + what);
    }

    public static Path statusMd(Path prpDir, String index) {
        return data(prpDir, index, "status.md");
    }

    public static Path timeline(Path prpDir, String index) {
        return data(prpDir, index, "status.jsonl");
    }

    public static Path meta(Path prpDir, String index) {
        return data(prpDir, index, "meta.json");
    }

    /** The n-th sub-file (n from 1): new slot n+1, legacy serial n. */
    public static Path sub(Path prpDir, String index, int n, String what, String ext) {
        var words = what == null || what.isBlank() ? "note" : what;
        if (PrpNumber.isLegacy(index)) return prpDir.resolve(index + "-prp." + String.format("%02d", n) + "." + words + "." + ext);
        return prpDir.resolve(index + base34(n + 1, 2) + "-" + words + "." + ext);
    }

    public static String log(String index, int serial, String ext) {
        return PrpNumber.isLegacy(index) ? String.format("%s-prp.99.%02d.logs.%s", index, serial, ext)
                : index + LOG_SLOT + "-" + base34(serial, 2) + ".logs." + ext;
    }

    /** A log file of either scheme: [number, serial, ext], or null. */
    public static String[] parseLog(String name) {
        var m = LOG.matcher(name);
        if (m.matches()) return new String[]{m.group(1), "" + value(m.group(2)), m.group(3).toLowerCase()};
        m = OLD_LOG.matcher(name);
        if (m.matches()) return new String[]{m.group(1).toUpperCase(), "" + Integer.parseInt(m.group(2)), m.group(3).toLowerCase()};
        return null;
    }

    /** True when the folder holds any legacy PRP file: writers keep the legacy scheme there until it is renumbered. */
    public static boolean legacyFolder(Path prpDir) throws IOException {
        if (prpDir == null || !Files.isDirectory(prpDir)) return false;
        try (var files = Files.list(prpDir)) {
            return files.filter(Files::isRegularFile).anyMatch(p -> isLegacy(p.getFileName().toString()));
        }
    }
}
