package littlejlib.proj.model;

/**
 * Where a project's source actually lives, as far as .git can tell.
 * <p>
 * Ordered worst-first so sorting the Git column by ascending value brings the concerns to the top:
 * a directory that was never put under version control is a bigger worry than one that is simply
 * hosted somewhere other than GitHub.
 */
public enum GitState {

    NONE("-", "no .git - this work is not under version control at all"),
    NO_REMOTE("local", "under git, but no remote - it has never been pushed anywhere"),
    OTHER_HOST("elsewhere", "pushed to a host other than GitHub"),
    GITHUB("github", "on GitHub");

    public final String label, description;

    GitState(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** True when a concern rather than a resting state - what the "not on GitHub" filter selects. */
    public boolean isConcern() {
        return this != GITHUB;
    }

    /** github.com and its enterprise-style subdomains, so a self-hosted mirror still reads as GitHub. */
    public static boolean isGitHubHost(String host) {
        if (host == null) return false;
        var h = host.toLowerCase();
        return h.equals("github.com") || h.endsWith(".github.com");
    }
}
