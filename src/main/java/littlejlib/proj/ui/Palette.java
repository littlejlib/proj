package littlejlib.proj.ui;

/**
 * Button tints, grouped so that actions of the same kind read as the same kind: navigation blue,
 * filesystem amber, clipboard purple, build teal, creation pink.
 */
public final class Palette {

    private Palette() { }

    public static final String
            BLUE = "-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0;",
            AMBER = "-fx-background-color: #FFF3E0; -fx-text-fill: #E65100;",
            PURPLE = "-fx-background-color: #F3E5F5; -fx-text-fill: #6A1B9A;",
            TEAL = "-fx-background-color: #E0F2F1; -fx-text-fill: #00695C;",
            PINK = "-fx-background-color: #FCE4EC; -fx-text-fill: #AD1457;",
            GREEN = "-fx-background-color: #E8F5E9; -fx-text-fill: #2E7D32;",
            GREY = "-fx-background-color: #ECEFF1; -fx-text-fill: #546E7A;";
}
