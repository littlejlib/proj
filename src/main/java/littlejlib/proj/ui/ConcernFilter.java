package littlejlib.proj.ui;

import littlejlib.proj.model.CentralStatus;
import littlejlib.proj.model.GitState;
import littlejlib.proj.model.ProjectConcerns;
import littlejlib.proj.model.ProjectRecord;

/**
 * The named things worth going looking for, as one control rather than a row of ad-hoc checkboxes.
 * <p>
 * Every entry here answers a question of the form "which of my projects are a problem", which is a
 * different job from the search box: the search box finds a project you already know the name of,
 * and this finds the ones you did not know to look for. Sorting the concern columns does the same
 * job from the other end, and both are kept because one is for browsing and one is for filtering.
 */
public enum ConcernFilter {

    ALL("All", "Everything, no concern filter"),

    MAVEN("Maven", "Maven projects"),
    NOT_MAVEN("Not maven", "No pom.xml - gradle, jbang, or a plain git checkout"),

    NOT_ON_CENTRAL("Not on Central", "Maven projects whose own coordinates are not published on Maven Central"),
    DEPS_NOT_ON_CENTRAL("Deps not on Central", "Projects depending on something that is not published - "
            + "which is what stops THEM being published"),
    HAS_LOCAL_DEPS("Has local deps", "Projects depending on another project in this index"),

    NOT_ON_GITHUB("Not on GitHub", "No .git, no remote, or a remote hosted somewhere other than GitHub"),
    NO_REMOTE("Never pushed", "Under git, but with no remote configured at all"),
    NO_GIT("Not in git", "Not under version control at all");

    public final String label, description;

    ConcernFilter(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * {@code concerns} is null for rows nothing has been computed for yet, which must never be
     * treated as passing a concern test - an unknown is not a finding.
     */
    public boolean test(ProjectRecord rec, ProjectConcerns concerns) {
        if (this == ALL) return true;
        if (rec == null || rec.isCodeDirNode()) return true;

        return switch (this) {
            case ALL -> true;
            case MAVEN -> rec.maven();
            case NOT_MAVEN -> !rec.maven();
            case NO_GIT -> rec.gitState() == GitState.NONE;
            case NO_REMOTE -> rec.gitState() == GitState.NO_REMOTE;
            case NOT_ON_GITHUB -> rec.gitState().isConcern();
            case NOT_ON_CENTRAL -> rec.maven() && concerns != null && concerns.central() == CentralStatus.ABSENT;
            case DEPS_NOT_ON_CENTRAL -> concerns != null && concerns.hasAbsentDependencies();
            case HAS_LOCAL_DEPS -> concerns != null && concerns.hasLocalDependencies();
        };
    }

    /** True when the answer depends on Central lookups, so the picker can say the view is provisional. */
    public boolean needsCentral() {
        return this == NOT_ON_CENTRAL || this == DEPS_NOT_ON_CENTRAL;
    }

    @Override
    public String toString() {
        return label;
    }
}
