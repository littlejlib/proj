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

    TERMINAL_CD("Term", 1, "Change this terminal's directory here", Palette.BLUE),
    OPEN_FOLDER("Fldr", 2, "Open in file explorer", Palette.AMBER),
    OPEN_CONSOLE("Cons", 3, "Open a console here", Palette.AMBER),
    EDIT_POM("POM", 4, "Edit pom.xml", Palette.TEAL),
    COPY_PATH("Path", 5, "Copy the full path", Palette.PURPLE),
    COPY_DEPENDENCY("CpDep", 6, "Copy as a maven <dependency> block", Palette.PURPLE),
    COPY_PROJECT("Repl", 7, "Replicate this project's structure under a new name", Palette.PINK),
    PIN_JACLI("Pin", 8, "Pin or unpin this project in jacli", Palette.GREEN),
    DEPENDENCIES("Deps", 9, "Show what this project depends on, and what depends on it", Palette.TEAL),
    PRP("PRP", 10, "Manage this project's PRPs", Palette.GREEN);

    public final String label, tooltip, style;
    public final int fKey;

    ProjectAction(String label, int fKey, String tooltip, String style) {
        this.label = label;
        this.fKey = fKey;
        this.tooltip = tooltip;
        this.style = style;
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
