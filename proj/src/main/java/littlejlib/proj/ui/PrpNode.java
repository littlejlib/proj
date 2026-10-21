package littlejlib.proj.ui;

import littlejlib.proj.prp.GlobalPrpEntry;

import java.nio.file.Path;

/**
 * One row of a PRP tree: either a PRP itself, or one of its files.
 * <p>
 * Both carry the owning {@link GlobalPrpEntry}, so a file row can answer the questions a file alone
 * cannot - which project it belongs to, and therefore what {@code @prp/...} should say and where an
 * agent started on it has to begin. Selecting a status file and being told "select a PRP first"
 * would be a distinction only this code cares about.
 * <p>
 * A missing node is the placeholder for a status file that has not been written yet. It is a row
 * rather than an absence because the whole point of showing it is to offer the key that creates it.
 */
public final class PrpNode {

    public final GlobalPrpEntry prp;
    public final Path file;          // null on the PRP row itself, and on the missing placeholder
    public final boolean root;
    public final boolean missing;
    public final String ticket;      // billing-38, or null to show the bare index

    PrpNode(GlobalPrpEntry prp, Path file, boolean root, boolean missing, String ticket) {
        this.prp = prp;
        this.file = file;
        this.root = root;
        this.missing = missing;
        this.ticket = ticket;
    }

    PrpNode(GlobalPrpEntry prp, Path file, boolean root, boolean missing) {
        this(prp, file, root, missing, null);
    }

    public static PrpNode of(GlobalPrpEntry prp) {
        return new PrpNode(prp, prp.getMainFile(), true, false);
    }

    public static PrpNode of(GlobalPrpEntry prp, String ticket) {
        return new PrpNode(prp, prp.getMainFile(), true, false, ticket);
    }

    public static PrpNode file(GlobalPrpEntry prp, Path file) {
        return new PrpNode(prp, file, false, false);
    }

    public static PrpNode missingStatus(GlobalPrpEntry prp) {
        return new PrpNode(prp, null, false, true);
    }

    /** What every action here acts on. Null only on the missing placeholder, which F3 then creates. */
    public Path target() {
        return root ? prp.getMainFile() : file;
    }

    public String label() {
        if (root) return (ticket == null ? prp.getIndex() : ticket) + "  " + prp.getName();
        if (missing) return prp.getIndex() + "-prp.status.md   (not created yet)";
        return file.getFileName().toString();
    }
}
