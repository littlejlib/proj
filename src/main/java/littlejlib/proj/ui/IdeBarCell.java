package littlejlib.proj.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import littlejlib.proj.ide.DetectedIde;
import littlejlib.proj.ide.ExeIcon;
import littlejlib.proj.model.ProjectRecord;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * One button per installed IDE, wearing that IDE's own icon, in a single column.
 * <p>
 * The icon comes from the executable itself, so the row is scannable by shape rather than by
 * reading four short words - and a column per IDE is not spent on something a 16px image says.
 */
public class IdeBarCell extends TreeTableCell<ProjectRecord, String> {

    final HBox box = new HBox(3);

    public IdeBarCell(List<DetectedIde> ides, BiConsumer<DetectedIde, ProjectRecord> onLaunch) {
        box.setAlignment(Pos.CENTER_LEFT);
        for (var ide : ides) {
            var button = new Button();
            button.setFocusTraversable(false);
            button.setStyle("-fx-padding: 1 4 1 4; -fx-background-radius: 3;" + Palette.GREY);
            button.setTooltip(new Tooltip("Open in " + ide.name + "\n" + ide.exe));

            var icon = ExeIcon.load(ide.exe, 16);
            if (icon != null) {
                var view = new ImageView(icon);
                view.setFitWidth(16);
                view.setFitHeight(16);
                button.setGraphic(view);
            } else {
                button.setText(ide.name.substring(0, Math.min(3, ide.name.length())));
                button.setStyle(button.getStyle() + "-fx-font-size: 9px;");
            }

            button.setOnAction(e -> {
                var rec = current();
                if (rec != null) onLaunch.accept(ide, rec);
            });
            box.getChildren().add(button);
        }
    }

    ProjectRecord current() {
        var row = getTreeTableRow();
        var item = row == null ? null : row.getTreeItem();
        return item == null ? null : item.getValue();
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty || current() == null ? null : box);
    }
}
