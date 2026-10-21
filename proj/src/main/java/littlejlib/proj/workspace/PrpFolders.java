package littlejlib.proj.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * What makes a directory a row in a PLAIN group, answered from ONE listing of its {@code prp}
 * folder.
 */
public final class PrpFolders {

    /** A PRP's main file: {@code 07-prp-name.md}, open or {@code .closed.md}. */
    static final Pattern MAIN = Pattern.compile("^\\d+-prp-.*\\.md$", Pattern.CASE_INSENSITIVE);

    public static final String PRP_DIR = "prp";

    private PrpFolders() {}

    /** The prp folder of a directory, or null - one probe, for where a full listing is not owed. */
    public static Path probe(Path dir) {
        var prp = dir.resolve(PRP_DIR);
        return Files.isDirectory(prp) ? prp : null;
    }

    public static boolean isPrpDir(Path dir) {
        return dir != null && dir.getFileName() != null
                && PRP_DIR.equalsIgnoreCase(dir.getFileName().toString());
    }

    /**
     * Fills in the PRP facts from an ALREADY LOCATED prp directory, and answers whether its owner
     * is a row at all. Takes the prp directory rather than its parent because the caller found it
     * in a listing it had to make anyway - asking {@code Files.isDirectory} again would be a second
     * network round trip for a fact already in hand.
     */
    public static boolean describe(Path prp, WorkspaceRecord into) {
        if (prp == null) return false;

        var mds = 0;
        var mains = 0;
        var newest = 0L;
        try (var files = Files.list(prp)) {
            for (var f : files.toList()) {
                var fileName = f.getFileName().toString();
                if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) continue;
                mds++;
                if (MAIN.matcher(fileName).matches()) mains++;
                newest = Math.max(newest, modified(f));
            }
        } catch (Exception e) {
            return false;
        }
        if (mds == 0) return false;

        into.hasPrp(true).prpCount(mains > 0 ? mains : mds);
        into.lastActivity(newest > 0 ? newest : modified(prp));
        return true;
    }

    static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }
}
