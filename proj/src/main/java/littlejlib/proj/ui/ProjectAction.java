package littlejlib.proj.ui;

import javafx.scene.input.KeyCode;
import littlejlib.proj.model.ProjectRecord;

import java.nio.file.Files;

/**
 * The per-row actions, absorbed from both tools. Each knows the function key that triggers it and
 * whether a given row supports it, which is what greys the button out - mm's supportsAction.
 * <p>
 * Function keys, not letters. The letters were unreachable in practice: the search field holds focus
 * for almost the whole life of the dialog, so pressing C to open a terminal typed a "c" into the
 * filter instead, and reaching the action meant first tabbing away from the box you were typing in.
 * A function key belongs to no text field, so it can be handled once at the scene and works wherever
 * the focus happens to be - and it needs no modifier, which matters here more than convention does.
 * <p>
 * GENSYMBOLS is deliberately absent: superseded by jacli, which also handles gradle.
 */
public enum ProjectAction {

    // F1 and F3 are two different terminals and the old names did not say so. F1 moves the shell you
    // are already sitting in and closes the picker, which is also what Enter does; F3 starts a
    // second one and leaves the picker up. Neither can do the other's job: F1 cannot open a window
    // because a process cannot cd its parent - the picker prints the path and a shell adapter does
    // the cd - and F3 cannot move your existing shell for the same reason.
    TERMINAL_CD("Go", "Go here (cd this shell)", "💻", Palette.INK, 1,
            "Pick this project and close: the terminal you started proj from changes to this "
                    + "directory. Same as Enter.", Palette.BLUE),
    OPEN_FOLDER("Fldr", "File explorer", "📂", Palette.MANILA, 2,
            "Open in file explorer", Palette.AMBER),
    OPEN_CONSOLE("Cons", "New console window", "⌨", Palette.SLATE, 3,
            "Open a NEW terminal window here. The picker stays open.", Palette.AMBER),
    EDIT_POM("POM", "Edit pom.xml", "📝", Palette.TEAL_INK, 4,
            "Edit pom.xml", Palette.TEAL),
    COPY_PATH("Path", "Copy path", "📋", Palette.PURPLE_INK, 5,
            "Copy the full path", Palette.PURPLE),
    COPY_DEPENDENCY("CpDep", "Copy dependency", "📦", Palette.CARDBOARD, 6,
            "Copy as a maven <dependency> block", Palette.PURPLE),
    COPY_PROJECT("Repl", "Replicate project", "🌱", Palette.LEAF, 7,
            "Replicate this project's structure under a new name", Palette.PINK),
    PIN_JACLI("Pin", "Pin in jacli", "🔎", Palette.SLATE, 8,
            "Pin or unpin this project in jacli", Palette.GREEN),
    DEPENDENCIES("Deps", "Dependencies", "🔗", Palette.LINK, 9,
            "Show what this project depends on, and what depends on it", Palette.TEAL),
    PRP("PRP", "PRPs", "📘", Palette.LINK, 10,
            "Manage this project's PRPs", Palette.GREEN);

    public final String label, full, icon, iconColor, tooltip, style;
    public final int fKey;

    ProjectAction(String label, String full, String icon, String iconColor, int fKey,
                  String tooltip, String style) {
        this.label = label;
        this.full = full;
        this.icon = icon;
        this.iconColor = iconColor;
        this.fKey = fKey;
        this.tooltip = tooltip;
        this.style = style;
    }

    /**
     * What a row button says beside its icon: the key and the abbreviation. Nine of these sit side
     * by side in one cell, so the name has to stay short - and the abbreviation is what
     * disambiguates the three copy-shaped actions, which no icon can do on its own.
     * <p>
     * The icon is not in this string. It is set as the button's GRAPHIC so it can be larger than the
     * 9px text beside it; inline it would be drawn at the label's size and be a smudge.
     */
    public String rowText() {
        return keyName() + " " + label;
    }

    /** What the legend says beside the same icon: the key, and the name written out. */
    public String legendText() {
        return keyName() + " " + full;
    }

    public String keyName() {
        return "F" + fKey;
    }

    public KeyCode keyCode() {
        return KeyCode.valueOf(keyName());
    }

    public static ProjectAction byKey(KeyCode code) {
        for (var a : values()) if (a.keyCode() == code) return a;
        return null;
    }

    public boolean supportedBy(ProjectRecord rec) {
        if (rec == null || rec.isCodeDirNode()) return false;
        return switch (this) {
            case TERMINAL_CD, OPEN_FOLDER, OPEN_CONSOLE, COPY_PATH, PRP -> true;
            case EDIT_POM -> rec.maven();
            case COPY_DEPENDENCY, COPY_PROJECT -> rec.maven() && !rec.coordinates().isBlank();
            case PIN_JACLI -> rec.maven() || rec.gradle();
            case DEPENDENCIES -> rec.maven();
        };
    }

    public boolean fileExists(ProjectRecord rec) {
        var dir = rec == null ? null : rec.dir();
        return dir != null && Files.isDirectory(dir);
    }
}
