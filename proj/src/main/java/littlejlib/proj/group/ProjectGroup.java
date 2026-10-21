package littlejlib.proj.group;

import littlejlib.proj.util.Slugged;
import datapotter.datahelper.Data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A named set of directories plus the kind of thing found inside them - one tab in the picker.
 */
@Data
public final class ProjectGroup extends ProjectGroup_A {

    String slug = "";
    String label = "";
    String kind = GroupKind.PLAIN.slug();

    /**
     * How far below a base to descend. Deliberately per group: the code bases are local disk and
     * five levels costs nothing, while a network filesystem is measured in seconds per level and
     * three reaches almost everything - 43 of 50 PRP folders on the base this was built for, at a
     * third of the cost of five.
     */
    int depth = 3;

    List<String> paths = new ArrayList<>();

    public static ProjectGroup of(String slug, String label, GroupKind kind, List<String> paths) {
        var g = new ProjectGroup();
        g.slug(slug).label(label).groupKind(kind);
        g.paths(new ArrayList<>(paths == null ? List.of() : paths));
        return g;
    }

    public GroupKind groupKind() {
        return Slugged.of(GroupKind.class, kind, GroupKind.PLAIN);
    }

    public ProjectGroup groupKind(GroupKind k) {
        return kind(Slugged.write(k, GroupKind.PLAIN));
    }

    /** The tab's text. Falls back to the slug so a half-written settings line still names itself. */
    public String displayLabel() {
        return label == null || label.isBlank() ? slug : label;
    }

    public List<Path> basePaths() {
        return paths.stream().filter(p -> p != null && !p.isBlank())
                .map(p -> Path.of(p.trim())).toList();
    }

    public List<Path> existingBasePaths() {
        return basePaths().stream().filter(Files::isDirectory).toList();
    }

    public boolean isCode() {
        return groupKind() == GroupKind.CODE;
    }

    public boolean holds(Path dir) {
        if (dir == null) return false;
        var target = dir.toAbsolutePath().normalize();
        return basePaths().stream().anyMatch(b -> target.startsWith(b.toAbsolutePath().normalize()));
    }

    @Override
    public String toString() {
        return displayLabel() + " (" + kind + ", " + paths.size() + " base"
                + (paths.size() == 1 ? "" : "s") + ")";
    }
}
