package littlejlib.proj.ui;

import java.util.List;

/**
 * The picker's columns, as an identity rather than a heading.
 * <p>
 * WHY AN ID AND NOT THE TITLE. Column order and visibility are remembered between runs, and a
 * remembered layout has to survive the heading being reworded - "Ver" becoming "Version" must not
 * silently reset everybody's columns, and a title is also what gets translated first. So the id is
 * what goes in the settings file and the title is only ever drawn. Ids are lowercase and stable;
 * change one and you have discarded that column's saved state, which is occasionally what you want
 * and never what you want by accident.
 * <p>
 * Declaration order is the DEFAULT order, used for a column the settings file does not mention -
 * which is every column, the first time, and any column added by a later version.
 */
public enum PickerColumn {

    NUM("num", "#", true),
    PROJECT("project", "Project", true),
    NAME("name", "Name", true),
    ARTIFACT("artifact", "Artifact", false),
    VERSION("ver", "Ver", true),
    BUILD("build", "Build", true),
    GIT("git", "Git", true),
    CENTRAL("central", "Central", true),
    DEPS("deps", "Deps", true),
    ACTIONS("actions", "Actions", true),
    IDES("ides", "Open in", true),
    DESCRIPTION("description", "Description", false),
    GIT_DETAILS("gitdetails", "Git details", false);

    public final String id, title;
    public final boolean shownByDefault;

    PickerColumn(String id, String title, boolean shownByDefault) {
        this.id = id;
        this.title = title;
        this.shownByDefault = shownByDefault;
    }

    /** Null for an id this version does not know - a settings file written by a later one. */
    public static PickerColumn byId(String id) {
        if (id == null) return null;
        var wanted = id.trim();
        for (var c : values()) if (c.id.equalsIgnoreCase(wanted)) return c;
        return null;
    }

    public static List<String> allIds() {
        return java.util.Arrays.stream(values()).map(c -> c.id).toList();
    }

    /**
     * The saved order, completed with whatever it does not mention.
     * <p>
     * Unknown ids are dropped and missing ones appended in declaration order, so a layout saved by
     * a version with fewer columns still opens - the new column simply arrives at the end instead
     * of the file being rejected or the layout being thrown away.
     */
    public static List<PickerColumn> order(List<String> savedIds) {
        var out = new java.util.LinkedHashSet<PickerColumn>();
        if (savedIds != null) {
            for (var id : savedIds) {
                var column = byId(id);
                if (column != null) out.add(column);
            }
        }
        out.addAll(java.util.Arrays.asList(values()));
        return List.copyOf(out);
    }
}
