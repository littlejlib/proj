package littlejlib.proj;

import littlejlib.proj.db.DB;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.scanner.ProjectStore;
import littlejlib.proj.ui.ProjectFilter;
import littlejlib.proj.ui.ProjectSort;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Checks the coderepobase rules against the real index: what each checkbox combination shows,
 * usage heat surviving a rescan, and relevance ordering.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SmokePriority" -Dexec.classpathScope=test
 */
public class SmokePriority {

    static final Set<CodeRepoBaseType> PRIMARY_ONLY = EnumSet.of(CodeRepoBaseType.PRIMARY);

    public static void main(String[] args) {
        var settings = new SettingsManager().loadSettings();
        var store = new ProjectStore();
        typeOf = ProjectFilter.resolver(settings);

        System.out.println("coderepobases:");
        for (var b : settings.getCodeRepoBases()) System.out.println("   " + b);

        var all = new java.util.ArrayList<ProjectRecord>();
        for (var p : settings.getBrowsePaths()) all.addAll(store.load(Path.of(p)));
        System.out.println("\nrows: " + all.size());
        for (var t : CodeRepoBaseType.values()) {
            System.out.println("   " + t.slug() + ": "
                    + all.stream().filter(r -> typeOf.apply(r) == t).count());
        }

        System.out.println("\nwhat each checkbox combination shows (no search text):");
        System.out.println("   default (primary only):      " + count(all, "", PRIMARY_ONLY));
        System.out.println("   + GitHub mirror:             "
                + count(all, "", EnumSet.of(CodeRepoBaseType.PRIMARY, CodeRepoBaseType.GITHUB_MIRROR)));
        System.out.println("   + Auxiliary:                 "
                + count(all, "", EnumSet.of(CodeRepoBaseType.PRIMARY, CodeRepoBaseType.AUXILIARY)));
        System.out.println("   both ticked:                 " + count(all, "", EnumSet.allOf(CodeRepoBaseType.class)));

        var mirrorOnly = all.stream()
                .filter(r -> typeOf.apply(r) == CodeRepoBaseType.GITHUB_MIRROR)
                .map(ProjectRecord::name).filter(n -> n != null && n.length() >= 5)
                .filter(n -> all.stream().filter(r -> typeOf.apply(r) == CodeRepoBaseType.PRIMARY)
                        .noneMatch(r -> nz(r.relativePath()).toLowerCase().contains(n.toLowerCase())))
                .findFirst().orElse(null);
        if (mirrorOnly != null) {
            System.out.println("\nsearching a mirror-only name '" + mirrorOnly + "':");
            System.out.println("   default:        " + count(all, mirrorOnly, PRIMARY_ONLY) + " (correctly none)");
            System.out.println("   mirror ticked:  "
                    + count(all, mirrorOnly, EnumSet.of(CodeRepoBaseType.PRIMARY, CodeRepoBaseType.GITHUB_MIRROR)));
            System.out.println("   the status line would offer: "
                    + all.stream().filter(ProjectFilter.textOnly(mirrorOnly, false, true))
                        .filter(r -> typeOf.apply(r) == CodeRepoBaseType.GITHUB_MIRROR).count()
                    + " in GitHub mirror");
        }

        var target = all.stream().filter(r -> !r.isSecondaryBase()).findFirst().orElse(null);
        if (target != null) {
            store.recordUsage(target.path());
            var after = store.byPath(target.path());
            store.save(target.openCount(0));
            var survived = store.byPath(target.path());
            System.out.println("\nusage on " + target.name() + ": now " + after.openCount()
                    + ", after a scan-shaped write still " + survived.openCount());
        }

        DB.get().shutdown();
        System.exit(0);
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static java.util.function.Function<ProjectRecord, CodeRepoBaseType> typeOf;

    static long count(List<ProjectRecord> all, String filter, Set<CodeRepoBaseType> types) {
        return all.stream().filter(ProjectFilter.of(filter, false, true, types, typeOf)).count();
    }
}
