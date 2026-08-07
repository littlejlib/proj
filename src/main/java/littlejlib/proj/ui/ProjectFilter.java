package littlejlib.proj.ui;

import littlejlib.proj.CodeRepoBaseType;
import littlejlib.proj.model.ProjectRecord;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The picker's row filter: which coderepobase types are included, the maven-only box, and the
 * search text.
 * <p>
 * Inclusion is by explicit checkbox rather than by any cleverness about how much you have typed.
 * An earlier version revealed the mirror once the filter reached three characters, which is the
 * kind of rule that is usually right and occasionally makes a project you know exists look
 * missing. A checkbox is always right about what it is doing.
 * <p>
 * Search terms are space separated and become a fuzzy regex ("xyz ai" matches xyz-ai, xyz_ai,
 * xyz anything ai), matched against the relative path AND the maven coordinates, so a groupId is
 * searchable - proj matched only the path, mm only the folder name.
 */
public final class ProjectFilter {

    private ProjectFilter() { }

    public static Predicate<ProjectRecord> of(String searchText, boolean mavenOnly, Set<CodeRepoBaseType> included,
                                              java.util.function.Function<ProjectRecord, CodeRepoBaseType> typeOf) {
        Predicate<ProjectRecord> base = rec -> (!mavenOnly || rec.maven()) && included.contains(typeOf.apply(rec));

        var text = searchText == null ? "" : searchText.trim();
        return text.isEmpty() ? base : base.and(textMatcher(text));
    }

    /**
     * Resolves a row's coderepobase type from its codeDir against the CURRENT settings, using the
     * stored baseType only as a fallback.
     * <p>
     * Settings are authoritative and the stored value is a denormalised convenience, so retyping a
     * coderepobase takes effect immediately rather than after a rescan. It also means rows written
     * before this column existed are classified correctly instead of defaulting to something.
     * <p>
     * The last resort is AUXILIARY, never PRIMARY: an unrecognised row - one whose coderepobase was
     * removed, say - should stay out of the default view rather than flood it.
     */
    public static java.util.function.Function<ProjectRecord, CodeRepoBaseType> resolver(
            littlejlib.proj.Settings settings) {
        var byCodeDir = new java.util.HashMap<String, CodeRepoBaseType>();
        for (var base : settings.getCodeRepoBases()) {
            var p = base.toPath();
            if (p != null) byCodeDir.put(p.toAbsolutePath().normalize().toString(), base.getType());
        }
        return rec -> {
            var known = byCodeDir.get(normalise(rec.codeDir()));
            return known != null ? known : CodeRepoBaseType.parse(rec.baseType(), CodeRepoBaseType.AUXILIARY);
        };
    }

    static String normalise(String path) {
        try {
            return path == null ? "" : java.nio.file.Path.of(path).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return path == null ? "" : path;
        }
    }

    /** Matches on text alone, so the picker can say how many hits are sitting in an excluded base. */
    public static Predicate<ProjectRecord> textOnly(String searchText, boolean mavenOnly) {
        Predicate<ProjectRecord> base = rec -> !mavenOnly || rec.maven();
        var text = searchText == null ? "" : searchText.trim();
        return text.isEmpty() ? base : base.and(textMatcher(text));
    }

    static Predicate<ProjectRecord> textMatcher(String text) {
        try {
            var pattern = Pattern.compile(String.join(".*", text.split("\\s+")), Pattern.CASE_INSENSITIVE);
            return rec -> pattern.matcher(haystack(rec)).find();
        } catch (Exception e) {
            var lower = text.toLowerCase();
            return rec -> haystack(rec).toLowerCase().contains(lower);
        }
    }

    static String haystack(ProjectRecord rec) {
        var rel = rec.relativePath() == null ? "" : rec.relativePath();
        var coords = rec.coordinates();
        return coords.isBlank() ? rel : rel + " " + coords;
    }
}
