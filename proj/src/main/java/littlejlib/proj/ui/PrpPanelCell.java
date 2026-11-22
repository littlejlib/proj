package littlejlib.proj.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One PRP row: a line of facts, and an action bar UNDER it that appears only when the row is hovered
 * or selected.
 * <p>
 * The bar used to be on every row, all the time, four buttons to the right of the text. Twenty PRPs
 * meant eighty buttons on screen at once, competing with the twenty things you were actually trying
 * to read - and the width they took pushed the name and the date into each other. Revealing them one
 * row at a time costs nothing to look at and puts the buttons where there is room to write what they
 * do rather than abbreviate it.
 * <p>
 * ON SELECTION as well as on hover, which the specification did not ask for and this user needs: the
 * hands condition makes the mouse the expensive way to work this window, and a bar reachable only by
 * hovering is a bar a keyboard user never sees. Arrowing down the list reveals each bar in turn, so
 * the keys are legible from the same place the pointer would find them. Exactly one row is selected,
 * so it adds no noise.
 * <p>
 * Unsupported actions are COLLAPSED here, not merely hidden as they are in
 * {@link ActionBarCell} - only one of these bars is on screen at a time, so there is no column of
 * buttons down the table to keep aligned, and holding an empty slot open would just be a hole.
 */
public class PrpPanelCell extends TreeCell<PrpNode> {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Grey against a pale row, pale against a dark one - see where it is used. */
    static final String MUTED = " -fx-text-fill: derive(-fx-text-background-color, 45%);";

    final boolean showProjectName;
    final VBox box = new VBox(3);
    final HBox line = new HBox(8);
    // A FlowPane, not an HBox: seven buttons do not fit a narrow side panel, and wrapping onto a
    // second line is better than being clipped by a cell that has been told not to ask for width.
    final javafx.scene.layout.FlowPane bar = new javafx.scene.layout.FlowPane(3, 3);
    final Label project = new Label(), index = new Label(), name = new Label(), status = new Label(),
            subCount = new Label(), when = new Label();
    final Map<PrpAction, Button> buttons = new LinkedHashMap<>();

    /**
     * @param showProjectName true in the global list, where the project is the first thing being
     *                        scanned for and the PRP number means nothing without it
     */
    public PrpPanelCell(BiConsumer<PrpAction, PrpNode> onAction, boolean showProjectName) {
        this.showProjectName = showProjectName;
        line.setAlignment(Pos.CENTER_LEFT);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 2, 0));

        // MUTED, but relative to whatever the row is painted. A fixed grey is muted on a pale row
        // and nearly illegible on the deep red a selected hot row now wears; derive() rides the
        // ladder, so it dims black text and stays light against a dark background.
        project.setStyle("-fx-font-size: 11; -fx-font-weight: bold;" + MUTED);
        project.setMaxWidth(140);
        project.setTextOverrun(OverrunStyle.ELLIPSIS);
        project.setVisible(showProjectName);
        project.setManaged(showProjectName);
        index.setStyle("-fx-font-weight: bold; -fx-min-width: 26; -fx-font-size: 12;");
        name.setStyle("-fx-font-size: 12;");
        subCount.setStyle("-fx-font-size: 9;" + MUTED);
        when.setStyle("-fx-font-size: 10;" + MUTED);

        // The NAME takes the slack, and ellipsises when there is none. A spacer between the name and
        // the badges would have been the obvious layout and is the wrong one: PRP names here run to
        // sixty characters, so the row demanded a width the panel does not have, the TreeView grew a
        // horizontal scrollbar, and the status and the date - the two things worth reading at a
        // glance - were the parts pushed off the right-hand edge.
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(name, Priority.ALWAYS);
        // The name is the only thing allowed to shrink. Left to itself an HBox shrinks everything
        // together, so a sixty-character PRP name ellipsised the status badge and the date down to
        // a pair of dots - trading away two short, fixed, entirely legible facts to save four
        // characters of a name that was going to be truncated either way.
        // The project column is in this list because it carries the TICKET, which is the row's
        // identifier: squeezed to "pr..." it identifies nothing, and a name that was going to be
        // truncated anyway is the right thing to spend the pixels on instead.
        for (var fixed : new Label[]{project, status, subCount, when}) {
            fixed.setMinWidth(Region.USE_PREF_SIZE);
        }
        line.getChildren().addAll(project, index, name, status, subCount, when);

        // Without this the cell asks for the width of its widest row and the panel scrolls sideways.
        setPrefWidth(0);
        setMaxWidth(Double.MAX_VALUE);

        for (var a : PrpAction.values()) {
            var b = new Button(a.rowText());
            b.setGraphic(Icons.of(a.icon, 12, a.iconColor));
            b.setGraphicTextGap(3);
            b.setFocusTraversable(false);
            b.setStyle("-fx-font-size: 9px; -fx-padding: 1 5 1 5; -fx-background-radius: 3;" + a.style);
            b.setTooltip(new Tooltip(a.tooltip + "   (" + a.keyName() + ")"));
            b.setOnAction(e -> {
                if (getItem() != null) onAction.accept(a, getItem());
            });
            buttons.put(a, b);
            bar.getChildren().add(b);
        }

        // The row grows when the bar appears, which is why managed is bound too: invisible alone
        // would reserve the height on every row and give away the whole saving.
        bar.visibleProperty().bind(hoverProperty().or(selectedProperty()));
        bar.managedProperty().bind(bar.visibleProperty());

        box.setPadding(new Insets(3, 6, 3, 6));
        box.getChildren().addAll(line, bar);
        selectedProperty().addListener((o, a, b) -> repaint());
    }

    /**
     * Same heat rule as the projects table, on the same scale, so "red" means the same thing on
     * both sides of the window: touched today. A PRP's activity is the newest mtime across all its
     * files, which is what makes a closed PRP whose logs were written this morning read as warm.
     * <p>
     * Only the PRP rows are painted, not their files: a heat bar down a run of sub-file rows would
     * be reporting the same fact three times, since a PRP's own heat IS the newest of them.
     */
    void repaint() {
        var node = getItem();
        Heat.paint(this, node == null || isEmpty() || !node.root
                ? -1 : Heat.of(node.prp.getLastModified()));
    }

    @Override
    protected void updateItem(PrpNode node, boolean empty) {
        super.updateItem(node, empty);
        if (empty || node == null) {
            setText(null);
            setGraphic(null);
            setStyle("");
            return;
        }
        if (node.root) paintRoot(node);
        else paintFile(node);
        repaint();

        buttons.forEach((a, b) -> {
            var ok = a.supportedBy(node);
            b.setVisible(ok);
            b.setManaged(ok);
        });

        setText(null);
        setGraphic(box);
    }

    void paintRoot(PrpNode node) {
        // ONE identifier, not two. Across projects a bare 32 says nothing, so the row leads with the
        // ticket - proj-32 - and the separate project column would then be saying it twice. Within
        // one project the key is the same on every row, so there the number alone is the answer and
        // the panel's own title says which project it belongs to.
        var ticketed = showProjectName && node.ticket != null;
        project.setText(ticketed ? node.ticket : node.prp.getProjectName());
        project.setVisible(showProjectName);
        project.setManaged(showProjectName);
        index.setText(node.prp.getIndex());
        index.setVisible(!ticketed);
        index.setManaged(!ticketed);
        name.setText(node.prp.getName());
        name.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        var st = node.prp.getStatus();
        status.setText(st.getLabel());
        status.setVisible(true);
        status.setManaged(true);
        status.setStyle("-fx-font-size: 9; -fx-padding: 2 6 2 6; -fx-background-radius: 2;"
                + "-fx-background-color: " + st.getBackgroundColor() + ";"
                + "-fx-text-fill: " + st.getTextColor() + ";");

        var subs = node.prp.getPrpEntry().getSubFileCount();
        subCount.setText(subs == 0 ? "" : subs + (subs == 1 ? " sub-file" : " sub-files"));
        when.setText(display(node.prp.getLastModified()));
    }

    void paintFile(PrpNode node) {
        project.setVisible(false);
        project.setManaged(false);
        index.setVisible(false);
        index.setManaged(false);
        status.setVisible(false);
        status.setManaged(false);
        subCount.setText("");

        name.setText(node.label());
        name.setStyle(node.missing
                ? "-fx-font-size: 11; -fx-font-style: italic;" + MUTED
                : "-fx-font-size: 11;");
        when.setText(node.missing ? "" : display(littlejlib.proj.prp.PrpScanner.mtime(node.file)));
    }

    static String display(long millis) {
        if (millis <= 0) return "";
        return WHEN.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
    }
}
