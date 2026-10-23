package littlejlib.proj.ui;

import javafx.scene.input.KeyCode;

import java.util.Arrays;

/** The Conversations tab's keys. The legend is built from this, so it cannot advertise an unbound key. */
public enum ConversationKey {

    LOG(KeyCode.F2, "F2 log", Palette.AMBER, "open the exported log of the selected hibernated conversation"),
    REFRESH(KeyCode.F5, "F5 refresh", Palette.GREY, "re-read running conversations, RAM and the hibernated list"),
    VIEW(KeyCode.F6, "F6 view", Palette.GREY, "hibernated / was hibernated / all"),
    ANYWAY(KeyCode.F7, "F7 hibernate anyway", Palette.PINK, "hibernate even if busy or not on a PRP (press twice)"),
    HIBERNATE(KeyCode.F8, "F8 hibernate", Palette.TEAL, "export log, record, close the tab (press twice)"),
    RESTORE(KeyCode.F9, "F9 restore", Palette.BLUE, "reopen the selected hibernated conversation in a new tab");

    public final KeyCode key;
    public final String label, style, tip;

    ConversationKey(KeyCode key, String label, String style, String tip) {
        this.key = key;
        this.label = label;
        this.style = style;
        this.tip = tip;
    }

    public static ConversationKey of(KeyCode code) {
        return Arrays.stream(values()).filter(k -> k.key == code).findFirst().orElse(null);
    }
}
