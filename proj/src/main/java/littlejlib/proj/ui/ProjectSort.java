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
    /**
     * Was "Recently modified", and was a different measurement from the row colour beside it - the
     * colour asked {@link Heat} for the newer of "when did you last open this" and "when did the
     * directory last change", and this asked only the second. So the hottest row in the table was
     * routinely not the top one, and two things claiming to rank the same list by the same idea
     * disagreed on screen.
     * <p>
     * One definition now, in {@code Heat}, used by both. Open count breaks ties and does no more
     * than that: it is a measure of habit, and a project you have opened four hundred times over two
     * years is not what "hot" is being asked about.
     */
    HOTTEST("Hottest");

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
            case HOTTEST -> {
                Comparator<ProjectRecord> byActivity = Comparator.comparingLong(ProjectSort::activity);
                Comparator<ProjectRecord> byOpens = Comparator.comparingInt(ProjectRecord::openCount);
                yield byActivity.reversed().thenComparing(byOpens.reversed()).thenComparing(byPath());
            }
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

    /** The same number the row colour is computed from, so the order and the tint cannot disagree. */
    static long activity(ProjectRecord r) {
        var newest = Heat.newest(r.lastOpened(), r.lastModified());
        return newest == null ? 0L : newest;
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String toString() {
        return label;
    }
}
