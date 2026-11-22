package littlejlib.proj.ui;

import littlejlib.proj.prp.GlobalPrpEntry;

import java.util.Comparator;

/**
 * The order a PRP list is read in.
 * <p>
 * Newest-first is the default because a prp folder is an append-only history: PRP 27 is what is
 * being worked on and PRP 01 was finished a year ago, so ascending order opens every window on the
 * least interesting end of the list and asks the user to scroll past their whole past to reach
 * their present. Ascending stays available because reading a project's history in the order it
 * happened is a real thing to want, just not the thing you want on open.
 * <p>
 * Recently active is a different question from either: it ranks by the newest mtime across ALL of a
 * PRP's files, so a closed PRP-14 whose logs were touched this morning outranks an untouched
 * PRP-26. That is the "what was I doing" order, and it is what the global tab uses.
 */
public enum PrpSort {

    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    RECENT("Recently active");

    public final String label;

    PrpSort(String label) {
        this.label = label;
    }

    public Comparator<GlobalPrpEntry> comparator() {
        var byIndex = Comparator.comparing(GlobalPrpEntry::getIndex,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        return switch (this) {
            case OLDEST -> byIndex;
            case NEWEST -> byIndex.reversed();
            // Ties broken by index rather than left to the scan order, so two PRPs written in the
            // same minute do not swap places between refreshes.
            case RECENT -> Comparator.comparingLong(GlobalPrpEntry::getLastModified)
                    .reversed().thenComparing(byIndex.reversed());
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
