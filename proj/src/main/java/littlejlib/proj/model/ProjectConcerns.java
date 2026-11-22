package littlejlib.proj.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The four questions the picker is being asked about a project at a glance: is it published, is it
 * pushed, what does it depend on, and is any of that going to stop it being published.
 * <p>
 * {@code unresolved} and {@code unchecked} are carried separately from {@code absent} rather than
 * folded into it. A dependency whose version could not be resolved, or whose Central lookup has not
 * run yet, is not a dependency that is missing from Central - and a column that showed those as
 * failures would be a column that cries wolf on a fresh index, which is the one thing that would
 * make the whole feature untrustworthy.
 */
public record ProjectConcerns(
        CentralStatus central,
        GitState git,
        int dependencyCount,
        List<ProjectDependency> localDependencies,
        List<ProjectDependency> absentDependencies,
        List<ProjectRecord> blockedBy,
        int unresolvedDependencies,
        int uncheckedDependencies) {

    public static ProjectConcerns of(ProjectRecord rec, DependencyIndex index,
                                     Function<String, CentralStatus> statusOf) {
        var deps = index.of(rec.path());

        var local = new ArrayList<ProjectDependency>();
        var absent = new ArrayList<ProjectDependency>();
        var blockedBy = new ArrayList<ProjectRecord>();
        var unresolved = 0;
        var unchecked = 0;

        for (var d : deps) {
            var provider = index.localProviderOf(d);
            if (provider != null) local.add(d);

            if (!d.isRuntimeFacing()) continue;      // a test-only gap does not block publication
            if (!d.versionResolved()) {
                unresolved++;
                continue;
            }

            var status = statusOf.apply(d.coordinates());
            if (status == CentralStatus.ABSENT) {
                absent.add(d);
                // A local project that is itself unpublished is the actionable form of this: it says
                // what YOU have to release, rather than that somebody else has not released.
                if (provider != null) blockedBy.add(provider);
            } else if (status != CentralStatus.PUBLISHED) {
                unchecked++;
            }
        }

        return new ProjectConcerns(
                statusOf.apply(rec.coordinates()), rec.gitState(), deps.size(),
                List.copyOf(local), List.copyOf(absent), List.copyOf(blockedBy), unresolved, unchecked);
    }

    public boolean notOnCentral() {
        return central == CentralStatus.ABSENT;
    }

    public boolean notOnGitHub() {
        return git.isConcern();
    }

    public boolean hasAbsentDependencies() {
        return !absentDependencies.isEmpty();
    }

    public boolean hasLocalDependencies() {
        return !localDependencies.isEmpty();
    }

    /** What the Deps column renders: counts, with the provisional ones kept visibly separate. */
    public String depsLabel() {
        if (dependencyCount == 0) return "";
        var sb = new StringBuilder().append(dependencyCount);
        if (!localDependencies.isEmpty()) sb.append("  ").append(localDependencies.size()).append(" local");
        if (!absentDependencies.isEmpty()) sb.append("  ").append(absentDependencies.size()).append(" absent");
        if (uncheckedDependencies > 0) sb.append("  ").append(uncheckedDependencies).append("?");
        if (unresolvedDependencies > 0) sb.append("  ").append(unresolvedDependencies).append(" unversioned");
        return sb.toString();
    }

    /**
     * Sorts worst first, so one click on the Deps header brings the projects that cannot be released
     * to the top. Absent dependencies outrank merely-local ones because they are the blocking kind.
     */
    public int severity() {
        return absentDependencies.size() * 1000 + localDependencies.size() * 10
                + (unresolvedDependencies > 0 ? 1 : 0);
    }
}
