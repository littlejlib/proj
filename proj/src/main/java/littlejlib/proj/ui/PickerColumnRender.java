package littlejlib.proj.ui;

import littlejlib.proj.central.CentralChecker;
import littlejlib.proj.model.CentralStatus;
import littlejlib.proj.model.ProjectConcerns;
import littlejlib.proj.model.ProjectRecord;

import java.util.function.Function;

/**
 * What each picker cell says, and what its tooltip says. Nothing here knows it is in a table.
 * <p>
 * Sorting a concern needs the concern to BE a value, so the concern columns carry the record itself
 * and one of these renderers, rather than a rendered string that would sort alphabetically by glyph.
 */
public final class PickerColumnRender {

    private PickerColumnRender() { }

    static String buildLabel(ProjectRecord r) {
        if (r.isCodeDirNode()) return "";
        if (r.isFolder()) return "folder";
        var sb = new StringBuilder();
        if (r.maven()) sb.append("mvn ");
        if (r.gradle()) sb.append("gr ");
        if (r.jbang()) sb.append("jb ");
        return sb.toString().trim();
    }

    static String buildTooltip(ProjectRecord r) {
        if (r.isFolder()) {
            return "a plain directory - no build file and nothing under git, "
                    + "but still somewhere to cd into, open, and keep PRPs in";
        }
        var label = buildLabel(r);
        return label.isEmpty()
                ? "no build file - a bare git checkout"
                : "maven=" + r.maven() + "  gradle=" + r.gradle() + "  jbang=" + r.jbang();
    }

    static String gitTooltip(ProjectRecord r) {
        var state = r.gitState();
        var remote = r.gitRemote();
        return remote == null || remote.isBlank() ? state.description : state.description + "\n" + remote;
    }

    /** Where the row sits and what it publishes - the two things the leaf name alone cannot say. */
    static String nameTooltip(ProjectRecord r) {
        if (r == null || r.isCodeDirNode()) return "";
        var sb = new StringBuilder();
        if (r.relativePath() != null && !r.relativePath().isBlank()) sb.append(r.relativePath());
        var coordinates = r.coordinates();
        if (!coordinates.isBlank()) sb.append(sb.length() == 0 ? "" : "\n").append(coordinates);
        return sb.toString();
    }

    static CentralStatus centralStatus(ProjectRecord r, CentralChecker checker) {
        if (r.isCodeDirNode() || !r.maven()) return CentralStatus.NOT_MAVEN;
        return checker.statusOf(r.coordinates());
    }

    static String centralTooltip(ProjectRecord r, CentralChecker checker) {
        var coords = r.coordinates();
        return switch (centralStatus(r, checker)) {
            case PUBLISHED -> coords + " is on Maven Central";
            case ABSENT -> coords + " was not found on Maven Central";
            case CHECKING -> "checking " + coords;
            case UNKNOWN -> "not looked up yet";
            case NOT_MAVEN -> "";
        };
    }

    /** Absent first: the point of sorting this column is to find what is NOT published. */
    static int centralSeverity(CentralStatus status) {
        return switch (status) {
            case ABSENT -> 0;
            case UNKNOWN -> 1;
            case CHECKING -> 2;
            case PUBLISHED -> 3;
            case NOT_MAVEN -> 4;
        };
    }

    static String depsTooltip(ProjectConcerns c) {
        if (c == null || c.dependencyCount() == 0) return "";
        var sb = new StringBuilder(c.dependencyCount() + " declared dependencies");
        if (!c.localDependencies().isEmpty()) {
            sb.append("\n\nLocal - provided by another project in this index:");
            c.localDependencies().forEach(d -> sb.append("\n   ").append(d.ga()));
        }
        if (!c.absentDependencies().isEmpty()) {
            sb.append("\n\nNot on Maven Central - this is what stops THIS project being published:");
            c.absentDependencies().forEach(d -> sb.append("\n   ").append(d.coordinates()));
        }
        if (c.uncheckedDependencies() > 0) {
            sb.append("\n\n").append(c.uncheckedDependencies())
              .append(" not looked up yet - the counts above are provisional");
        }
        if (c.unresolvedDependencies() > 0) {
            sb.append("\n").append(c.unresolvedDependencies())
              .append(" with no resolvable version, so they cannot be checked at all");
        }
        return sb.toString();
    }

    static String label(ProjectConcerns c, Function<ProjectConcerns, String> get) {
        return c == null ? "" : get.apply(c);
    }
}
