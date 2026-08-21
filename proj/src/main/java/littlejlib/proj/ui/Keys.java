package littlejlib.proj.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * The one keyboard rule this application does not break: a bare letter or digit belongs to whatever
 * the user is typing, never to a control.
 * <p>
 * JavaFX breaks it by default. A {@code _} in a Button or CheckBox label registers a mnemonic, and
 * {@code KeyboardShortcutsHandler} fires mnemonics when {@code isAltDown() || mnemonicsDisplayEnabled}
 * - where that second flag is latched on by the first Alt press in the Scene and never reliably
 * cleared. So the mnemonic starts out needing Alt, and after any Alt press in the window fires on the
 * bare letter instead, stealing it from the filter box and moving the focus. A daemon that keeps one
 * Scene alive across every open latches it once and keeps it latched.
 * <p>
 * {@link #noMnemonics} is therefore applied to every dialog root here, and shortcuts are declared
 * explicitly: function keys with no modifier, or {@link #alt} for the secondary toggles.
 */
public final class Keys {

    private Keys() { }

    /** Strips mnemonics from a whole dialog, tab contents included - they are built lazily. */
    public static void noMnemonics(Node node) {
        if (node instanceof Labeled l) l.setMnemonicParsing(false);
        if (node instanceof TabPane tabs) {
            tabs.getTabs().forEach(t -> { if (t.getContent() != null) noMnemonics(t.getContent()); });
        }
        if (node instanceof Parent p) p.getChildrenUnmodifiable().forEach(Keys::noMnemonics);
    }

    /** Alt and nothing else, so a shortcut cannot be reached by accident from a shifted key. */
    public static boolean alt(KeyEvent e, KeyCode code) {
        return e.getCode() == code
                && e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown();
    }

    public static boolean plain(KeyEvent e, KeyCode code) {
        return e.getCode() == code
                && !e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown();
    }
}
