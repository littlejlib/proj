package littlejlib.proj.group;

import littlejlib.proj.CodeRepoBase;
import littlejlib.proj.Settings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The groups this machine has, in tab order, code first.
 */
public final class Groups {

    public static final String CODE_SLUG = "code";
    public static final String CODE_LABEL = "Code";

    private Groups() {}

    /** Every group, code first, then the configured ones in the order they were written. */
    public static List<ProjectGroup> of(Settings settings) {
        var out = new ArrayList<ProjectGroup>();
        out.add(code(settings));
        out.addAll(plain(settings));
        return out;
    }

    /** The configured non-code groups, in declaration order. */
    public static List<ProjectGroup> plain(Settings settings) {
        return settings.getProjectGroups().stream()
                .filter(g -> !CODE_SLUG.equalsIgnoreCase(g.slug()))
                .filter(g -> !g.paths().isEmpty())
                .toList();
    }

    public static ProjectGroup code(Settings settings) {
        var g = ProjectGroup.of(CODE_SLUG, codeLabel(settings), GroupKind.CODE,
                settings.getCodeRepoBases().stream().map(CodeRepoBase::getPath).toList());
        return g.depth(settings.getProjectScanningDepth());
    }

    static String codeLabel(Settings settings) {
        return settings.getProjectGroups().stream()
                .filter(g -> CODE_SLUG.equalsIgnoreCase(g.slug()))
                .map(ProjectGroup::displayLabel).findFirst().orElse(CODE_LABEL);
    }

    public static ProjectGroup bySlug(Settings settings, String slug) {
        if (slug == null || slug.isBlank()) return null;
        return of(settings).stream().filter(g -> slug.equalsIgnoreCase(g.slug())).findFirst().orElse(null);
    }

    /**
     * Which group a directory belongs to, or null. This is what lets a transcript from anywhere on
     * the machine be shown on the right tab instead of being told to run a scan that would never
     * have found it.
     */
    public static ProjectGroup holding(Settings settings, Path dir) {
        return of(settings).stream().filter(g -> g.holds(dir)).findFirst().orElse(null);
    }
}
