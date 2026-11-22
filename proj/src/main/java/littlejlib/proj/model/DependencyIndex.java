package littlejlib.proj.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The local dependency graph: which of your own projects each project depends on, and which of them
 * depend on it.
 * <p>
 * "Local" means the dependency's groupId:artifactId is the coordinates of another project sitting in
 * the index - which is exactly the case where "is it on Maven Central" stops being a question about
 * somebody else's release and becomes a question about your own. Matched on groupId:artifactId
 * rather than on the full coordinates on purpose: depending on version 1.0 of a project that is
 * now at 1.1 is still depending on that project, and pretending otherwise would hide the link at
 * precisely the moment it matters.
 */
public final class DependencyIndex {

    final Map<String, ProjectRecord> projectByGa = new HashMap<>();
    final Map<String, ProjectRecord> projectByPath = new HashMap<>();
    final Map<String, List<ProjectDependency>> byProject = new HashMap<>();
    final Map<String, List<ProjectDependency>> byGa = new HashMap<>();

    public DependencyIndex(Collection<ProjectRecord> projects, Collection<ProjectDependency> dependencies) {
        for (var p : projects) {
            projectByPath.put(p.path(), p);
            if (p.maven() && p.groupId() != null && p.artifactId() != null) {
                projectByGa.putIfAbsent(ProjectDependency.ga(p.groupId(), p.artifactId()), p);
            }
        }
        for (var d : dependencies) {
            byProject.computeIfAbsent(d.path(), k -> new ArrayList<>()).add(d);
            byGa.computeIfAbsent(d.ga(), k -> new ArrayList<>()).add(d);
        }
    }

    public List<ProjectDependency> of(String projectPath) {
        return byProject.getOrDefault(projectPath, List.of());
    }

    public int dependencyCount(String projectPath) {
        return of(projectPath).size();
    }

    /** The project in this index that provides a dependency, or null when it is somebody else's. */
    public ProjectRecord localProviderOf(ProjectDependency dep) {
        return dep == null ? null : projectByGa.get(dep.ga());
    }

    public boolean isLocal(ProjectDependency dep) {
        return localProviderOf(dep) != null;
    }

    public List<ProjectDependency> localDependenciesOf(String projectPath) {
        return of(projectPath).stream().filter(this::isLocal).toList();
    }

    /** Which projects in the index declare a dependency on this one. The reverse of the above. */
    public List<ProjectRecord> dependentsOf(ProjectRecord project) {
        if (project == null || !project.maven() || project.groupId() == null || project.artifactId() == null) {
            return List.of();
        }
        var ga = ProjectDependency.ga(project.groupId(), project.artifactId());
        var out = new LinkedHashSet<ProjectRecord>();
        for (var d : byGa.getOrDefault(ga, List.of())) {
            var owner = projectByPath.get(d.path());
            if (owner != null && !owner.path().equals(project.path())) out.add(owner);
        }
        return new ArrayList<>(out);
    }

    /**
     * Every coordinate worth asking Maven Central about: each project's own, and each dependency's.
     * <p>
     * The dependencies are included because "this project's dependencies are not on Central" is a
     * question about the dependencies, and answering it from the projects alone would only ever
     * cover the handful that happen to be ours.
     */
    public List<String> allCoordinates() {
        var out = new LinkedHashSet<String>();
        for (var p : projectByPath.values()) {
            var coords = p.coordinates();
            if (!coords.isBlank() && p.version() != null && !p.version().contains("${")) out.add(coords);
        }
        for (var list : byProject.values()) {
            for (var d : list) {
                var coords = d.coordinates();
                if (!coords.isBlank()) out.add(coords);
            }
        }
        return new ArrayList<>(out);
    }

    public ProjectRecord byPath(String path) {
        return projectByPath.get(path);
    }
}
