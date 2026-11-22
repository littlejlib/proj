package littlejlib.proj.ui;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.ImageView;
import littlejlib.proj.ide.DetectedIde;
import littlejlib.proj.ide.ExeIcon;
import littlejlib.proj.model.ProjectRecord;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * The row actions and the IDE launchers as one menu, rather than as two columns of buttons.
 * <p>
 * Between them the two columns cost around 730 pixels of a table already wider than the window, and
 * spent it on every row at once to offer something wanted on one row at a time. A menu costs nothing
 * until it is asked for, and it can afford to write the action out in full - so this carries the
 * same three things the legend does, key and icon and full name, at the moment of use.
 * <p>
 * Each item is a coloured chip carrying its action's own tint, the SAME tint the legend chip at the
 * bottom of the window wears. A menu of default-background rows would have thrown that away, and the
 * colour is half of how these are told apart at a glance.
 * <p>
 * Actions the row does not support are left out rather than greyed. A menu has no columns to keep
 * aligned, so there is nothing to be gained by holding a slot open for them.
 */
public final class ActionContextMenu {

    private ActionContextMenu() { }

    public static ContextMenu of(List<ProjectAction> actions, List<DetectedIde> ides, ProjectRecord rec,
                                 BiConsumer<ProjectAction, ProjectRecord> onAction,
                                 BiConsumer<DetectedIde, ProjectRecord> onLaunch) {
        var menu = new ContextMenu();

        // The menu opens under the pointer, away from the row that raised it, so it says which
        // project it is about. Disabled because it is a heading and not an action.
        var heading = new MenuItem(rec.name());
        heading.setDisable(true);
        menu.getItems().addAll(heading, new SeparatorMenuItem());

        for (var a : actions) {
            if (!a.supportedBy(rec)) continue;
            var item = new MenuItem();
            item.setGraphic(chipFor(a));
            item.setOnAction(e -> onAction.accept(a, rec));
            menu.getItems().add(item);
        }

        if (ides != null && !ides.isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
            for (var ide : ides) {
                var item = new MenuItem();
                item.setGraphic(chipFor(ide));
                item.setOnAction(e -> onLaunch.accept(ide, rec));
                menu.getItems().add(item);
            }
        }
        return menu;
    }

    /** Public so {@code SmokeIcons} previews the real thing rather than a copy that can drift. */
    public static Label chipFor(ProjectAction a) {
        return chip(a.keyName() + "   " + a.full, a.style, Icons.of(a.icon, 15, a.iconColor));
    }

    public static Label chipFor(DetectedIde ide) {
        return chip("Open in " + ide.name, Palette.GREY, ideIcon(ide));
    }

    /**
     * The IDE's own icon, taken out of its executable - the one icon here that is genuinely in
     * colour, because it is an image rather than a glyph. Falls back to the first letters of the
     * name on anything the icon cannot be read from.
     */
    static javafx.scene.Node ideIcon(DetectedIde ide) {
        var image = ExeIcon.load(ide.exe, 16);
        if (image == null) return Icons.of(ide.name.substring(0, Math.min(2, ide.name.length())), 12, null);
        var view = new ImageView(image);
        view.setFitWidth(16);
        view.setFitHeight(16);
        return view;
    }

    static Label chip(String text, String style, javafx.scene.Node icon) {
        var chip = new Label(text);
        chip.setGraphic(icon);
        chip.setGraphicTextGap(7);
        chip.setStyle("-fx-font-size: 11px; -fx-padding: 2 9 2 7; -fx-background-radius: 3;" + style);
        return chip;
    }
}
