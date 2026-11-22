package littlejlib.proj.cct.ui;

import module java.base;

import javafx.scene.Node;
import javafx.scene.control.*;

/**
 * Every action on this tab, with its key beside it, on one key.
 * <p>
 * WHY THIS EXISTS. The flat scheme - one action, one function key - ran out. F1 to F12 were all
 * spoken for and this round added export, find-in-transcript, select-all, open-the-PRP,
 * activate-the-project and a shell selector. Binding those to chords would put six things the user
 * needs on exactly the gesture their hands cannot make. So the hot path keeps its function keys and
 * everything else lives here, where the key is READ rather than remembered - which serves the
 * permanent-legend rule better than a status bar that no longer fits.
 * <p>
 * Also the row context menu, because the same list is the answer to a right-click.
 */
public final class ActionMenu {

    private final ContextMenu menu = new ContextMenu();
    private final List<Runnable> refresh = new ArrayList<>();

    public ActionMenu() {
        menu.setAutoHide(true);
    }

    public ContextMenu menu() { return menu; }

    public void add(String key, String label, Runnable action) {
        add(key, label, action, () -> true);
    }

    /** {@code enabled} is asked every time the menu opens, so an action greys out rather than lying. */
    public void add(String key, String label, Runnable action, Supplier<Boolean> enabled) {
        var item = new MenuItem(key.isBlank() ? label : label + "        " + key);
        item.setMnemonicParsing(false);
        item.setOnAction(e -> action.run());
        menu.getItems().add(item);
        refresh.add(() -> item.setDisable(!Boolean.TRUE.equals(enabled.get())));
    }

    public void separator() { menu.getItems().add(new SeparatorMenuItem()); }

    public void show(Node anchor) {
        refresh.forEach(Runnable::run);
        if (menu.isShowing()) { menu.hide(); return; }
        if (anchor == null) return;
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds == null) return;
        menu.show(anchor, bounds.getMinX() + 20, bounds.getMinY() + 30);
    }

    public void showAt(Node anchor, double screenX, double screenY) {
        refresh.forEach(Runnable::run);
        menu.show(anchor, screenX, screenY);
    }
}
