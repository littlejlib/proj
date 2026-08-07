package littlejlib.proj.ui;

import littlejlib.proj.model.ProjectRecord;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How siblings are ordered. Applied at every level of the tree, so the hierarchy always holds and
 * the ordering only decides the run of children under one parent.
 */
public enum ProjectSort {

    RELEVANCE("Relevance"),
    PATH("Path"),
    NAME("Name"),
    RECENT("Recently modified");

    public final String label;

    ProjectSort(String label) {
        this.label = label;
    }

    public Comparator<ProjectRecord> comparator(Map<String, Integer> orgWeight) {
        return comparator(orgWeight, ProjectRecord::isSecondaryBase);
    }

    /** {@code secondary} decides what sorts below primary; the picker supplies the settings-aware one. */
    public Comparator<ProjectRecord> comparator(Map<String, Integer> orgWeight,
                                                java.util.function.Predicate<ProjectRecord> secondary) {
        return switch (this) {
            case PATH -> byPath();
            case NAME -> Comparator.comparing(ProjectSort::name, String.CASE_INSENSITIVE_ORDER);
            case RECENT -> Comparator.comparingLong(ProjectSort::modified).reversed().thenComparing(byPath());
            case RELEVANCE -> {
                Comparator<ProjectRecord> heat = Comparator.comparingInt(ProjectRecord::openCount);
                Comparator<ProjectRecord> org =
                        Comparator.comparingInt(r -> orgWeight.getOrDefault(nz(r.org()), 0));
                yield Comparator.comparingInt((ProjectRecord r) -> secondary.test(r) ? 1 : 0)
                        .thenComparing(heat.reversed())
                        .thenComparing(org.reversed())
                        .thenComparing(byPath());
            }
        };
    }

    /**
     * Organisation importance, inferred rather than configured: an organisation is as important as
     * the projects you actually open inside it. Asking the user to rank their orgs by hand would
     * be a config file that goes stale the week after it is written.
     */
    public static Map<String, Integer> orgWeights(List<ProjectRecord> records) {
        return records.stream().collect(Collectors.groupingBy(
                r -> nz(r.org()), Collectors.summingInt(ProjectRecord::openCount)));
    }

    static Comparator<ProjectRecord> byPath() {
        return Comparator.comparing(ProjectSort::path, String.CASE_INSENSITIVE_ORDER);
    }

    static String path(ProjectRecord r) {
        return nz(r.relativePath());
    }

    static String name(ProjectRecord r) {
        return nz(r.name());
    }

    static long modified(ProjectRecord r) {
        return r.lastModified() == null ? 0L : r.lastModified();
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String toString() {
        return label;
    }
}
