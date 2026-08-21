package littlejlib.proj.cct.ui;

import module java.base;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;

public final class Keys {

    private Keys() {}

    public static void disableMnemonics(Parent root) {
        walk(root);
    }

    private static void walk(Node node) {
        if (node instanceof Labeled l) l.setMnemonicParsing(false);
        if (node instanceof MenuBar bar) bar.getMenus().forEach(Keys::menu);
        if (node instanceof TabPane tabs) {
            for (var t : tabs.getTabs()) if (t.getContent() != null) walk(t.getContent());
        }
        if (node instanceof SplitPane sp) sp.getItems().forEach(Keys::walk);
        if (node instanceof ScrollPane sc && sc.getContent() != null) walk(sc.getContent());
        if (node instanceof TitledPane tp && tp.getContent() != null) walk(tp.getContent());
        if (node instanceof Parent p) p.getChildrenUnmodifiable().forEach(Keys::walk);
    }

    private static void menu(Menu m) {
        m.setMnemonicParsing(false);
        for (var it : m.getItems()) {
            it.setMnemonicParsing(false);
            if (it instanceof Menu sub) menu(sub);
        }
    }
}
