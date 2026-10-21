package littlejlib.proj.ui;

import littlejlib.proj.workspace.WorkspaceRecord;

import java.util.Comparator;

/**
 * How folders of a PLAIN group are ordered - the counterpart of {@link ProjectSort}, mapped onto
 * what a documents tree actually has.
 */
public enum WorkspaceSort {

    HOTTEST("Hottest"),
    NAME("Name"),
    PATH("Path"),
    PRPS("Most PRPs");

    public final String label;

    WorkspaceSort(String label) {
        this.label = label;
    }

    public Comparator<WorkspaceRecord> comparator() {
        var byName = Comparator.comparing(
                (WorkspaceRecord r) -> r.name() == null ? "" : r.name(), String.CASE_INSENSITIVE_ORDER);
        return switch (this) {
            case HOTTEST -> Comparator.comparingLong(WorkspaceRecord::activity).reversed().thenComparing(byName);
            case NAME -> byName;
            case PATH -> Comparator.comparing(
                    (WorkspaceRecord r) -> r.relativePath() == null ? "" : r.relativePath(),
                    String.CASE_INSENSITIVE_ORDER);
            // Folders carrying nothing sort last whatever their date, which is the point of asking.
            case PRPS -> Comparator.comparingInt((WorkspaceRecord r) -> r.hasPrp() ? r.prpCount() : -1)
                    .reversed()
                    .thenComparing(Comparator.comparingLong(WorkspaceRecord::activity).reversed());
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
