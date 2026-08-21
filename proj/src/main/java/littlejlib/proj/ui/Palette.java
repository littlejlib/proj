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

    /**
     * Ink for the icon glyphs, which is a different job from the chip tints above.
     * <p>
     * JavaFX has no colour-font support, so an emoji arrives as a monochrome outline painted in the
     * text fill - every icon the same near-black, which is what made a row of them read as texture
     * rather than as ten different things. These are picked for the OBJECT rather than for the
     * chip: a folder is manila, a package is cardboard, a sprout is green, a terminal is near-black.
     * The chip tints stay pale enough that a natural colour sits on any of them.
     */
    public static final String
            INK = "#263238",
            SLATE = "#455A64",
            MANILA = "#F9A825",
            CARDBOARD = "#8D6E63",
            LEAF = "#2E7D32",
            LINK = "#0277BD",
            TEAL_INK = "#00695C",
            PURPLE_INK = "#6A1B9A";
}
