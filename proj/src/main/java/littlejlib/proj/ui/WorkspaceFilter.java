package littlejlib.proj.ui;

import littlejlib.proj.workspace.WorkspaceRecord;

/**
 * What to show in a PLAIN group - the counterpart of {@link ConcernFilter}.
 */
public enum WorkspaceFilter {

    ALL("All", "Every folder, whether it is a project or not"),
    PROJECTS("Projects", "Folders that carry PRPs, or whose name is dated - both count"),
    PROJECTS_AND_REPOS("Projects + repos", "Projects, and the folders that hold them"),
    WITH_PRPS("With PRPs", "Folders that have a prp directory with something in it"),
    WITHOUT_PRPS("Without PRPs", "Folders no PRP has been written for yet"),
    RECENT("Touched this month", "Anything with activity in the last 30 days");

    static final long MONTH = 30L * 24 * 60 * 60 * 1000;

    public final String label, description;

    WorkspaceFilter(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public boolean test(WorkspaceRecord r, long now) {
        return switch (this) {
            case ALL -> true;
            case PROJECTS -> r.isProject();
            case PROJECTS_AND_REPOS -> r.isProject() || r.isContainer();
            case WITH_PRPS -> r.hasPrp();
            case WITHOUT_PRPS -> !r.hasPrp();
            case RECENT -> r.activity() > 0 && now - r.activity() <= MONTH;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
