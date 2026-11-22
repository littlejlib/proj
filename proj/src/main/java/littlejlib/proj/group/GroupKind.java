package littlejlib.proj.group;

import littlejlib.proj.util.Slugged;

/**
 * What a group's rows ARE. This is the only thing that decides how they are found, what is stored
 * about them, and which controls the tab carries.
 */
public enum GroupKind implements Slugged {

    CODE("Code", "repositories - a pom, a git directory, gradle or jbang makes a row"),
    PLAIN("Plain", "folders that carry PRPs, and nothing else is assumed");

    public final String label, description;

    GroupKind(String label, String description) {
        this.label = label;
        this.description = description;
    }

    @Override
    public String slug() {
        return this == CODE ? "code" : "plain";
    }

    /** The slug and the constant name are handled by {@link Slugged}; the rest are what people type. */
    public static GroupKind parse(String value, GroupKind fallback) {
        var canonical = Slugged.of(GroupKind.class, value, null);
        if (canonical != null) return canonical;
        if (value == null || value.isBlank()) return fallback;
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "MAVEN", "REPO", "REPOSITORIES" -> CODE;
            case "PRP", "DOCS", "DOCUMENTS", "WORKSPACE" -> PLAIN;
            default -> fallback;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
