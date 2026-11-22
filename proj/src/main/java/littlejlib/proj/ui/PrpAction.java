package littlejlib.proj.ui;

import javafx.scene.input.KeyCode;

/**
 * What can be done to one PRP, or to one of its files.
 * <p>
 * The numbering is the one both PRP windows have used since PRP-24 and is deliberately unchanged:
 * F2 opens, F4 copies a path, F7 toggles. The list is now shown in three places - the popup, the
 * side panel beside the projects, and the global tab - and a key that meant something different in
 * each would be worse than no key at all.
 * <p>
 * These share their numbers with {@link ProjectAction}, which acts on PROJECTS in the same window.
 * That is resolved by focus, not by mode: whichever pane holds the focus owns the F-keys, the
 * focused pane wears a coloured header, and the legend along the bottom swaps to match. See
 * {@code ProjectPickerDialog.onGlobalKey}.
 */
public enum PrpAction {

    OPEN("Open", "Open in editor", "📄", Palette.LINK, 2,
            "Open this file in the configured editor", Palette.GREEN),
    STATUS("Status", "Status file", "📑", Palette.TEAL_INK, 3,
            "Open this PRP's status file, creating it from the template if it does not exist yet",
            Palette.TEAL),
    COPY_PATH("Path", "Copy path", "📋", Palette.PURPLE_INK, 4,
            "Copy the full absolute path", Palette.PURPLE),
    COPY_CLAUDE("Claude", "Copy @prp/ path", "📎", Palette.CARDBOARD, 5,
            "Copy as @prp/... - relative to the PRP's OWN project, which is what an agent needs",
            Palette.PURPLE),
    FOLDER("Fldr", "Open prp folder", "📂", Palette.MANILA, 6,
            "Open the prp folder in file explorer", Palette.AMBER),
    TOGGLE("Toggle", "Toggle active/closed", "🔁", Palette.SLATE, 7,
            "Rename between NN-prp-name.md and NN-prp-name.closed.md", Palette.PINK),
    AGENT("Agent", "Start coding agent", "🚀", Palette.LEAF, 8,
            "Open a terminal at the PROJECT root - not the prp folder - running the coding agent "
                    + "on this PRP", Palette.BLUE),
    RESUME("Resume", "Resume conversation", "↩", Palette.LEAF, 9,
            "Reopen the last claude-code conversation this PRP had, in the same tab name. Only "
                    + "sessions proj started are known; for the rest, the Transcripts tab",
            Palette.TEAL);

    public final String label, full, icon, iconColor, tooltip, style;
    public final int fKey;

    PrpAction(String label, String full, String icon, String iconColor, int fKey,
              String tooltip, String style) {
        this.label = label;
        this.full = full;
        this.icon = icon;
        this.iconColor = iconColor;
        this.fKey = fKey;
        this.tooltip = tooltip;
        this.style = style;
    }

    public String rowText() {
        return keyName() + " " + label;
    }

    public String legendText() {
        return keyName() + " " + full;
    }

    public String keyName() {
        return "F" + fKey;
    }

    public KeyCode keyCode() {
        return KeyCode.valueOf(keyName());
    }

    public static PrpAction byKey(KeyCode code) {
        for (var a : values()) if (a.keyCode() == code) return a;
        return null;
    }

    /**
     * A file row is a file: it opens and its path copies, and nothing else applies to it. The three
     * that do not - the status file, the closed/open rename, and starting an agent - are all
     * properties of the PRP as a whole, and offering them on a sub-file would either act on the
     * wrong thing or have to explain why it did not.
     */
    public boolean supportedBy(PrpNode node) {
        if (node == null) return false;
        if (node.missing) return this == STATUS || this == FOLDER;
        return switch (this) {
            case OPEN, COPY_PATH, COPY_CLAUDE, FOLDER -> true;
            case STATUS, TOGGLE, AGENT, RESUME -> node.root;
        };
    }
}
