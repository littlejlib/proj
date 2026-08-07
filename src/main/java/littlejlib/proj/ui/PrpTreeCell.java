package littlejlib.proj.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import littlejlib.proj.Settings;
import littlejlib.proj.ui.PrpManagerDialog.PrpTreeNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PrpTreeCell extends TreeCell<PrpTreeNode> {

    private final Settings settings;
    private final Path prpDir;
    private final List<littlejlib.proj.prp.PrpEntry> allPrps;

    public PrpTreeCell(Settings settings, Path prpDir, List<littlejlib.proj.prp.PrpEntry> allPrps) {
        this.settings = settings;
        this.prpDir = prpDir;
        this.allPrps = allPrps;
    }

    @Override
    protected void updateItem(PrpTreeNode node, boolean empty) {
        super.updateItem(node, empty);

        if (empty || node == null) {
            setGraphic(null);
            setText(null);
        } else if (node.isRoot) {
            updateRootCell(node);
        } else {
            updateLeafCell(node);
        }
    }

    private void updateRootCell(PrpTreeNode node) {
        HBox rootCell = new HBox(8);
        rootCell.setPadding(new Insets(6, 8, 6, 8));
        rootCell.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        // Index label
        Label indexLabel = new Label(node.prp.getIndex());
        indexLabel.setStyle("-fx-font-weight: bold; -fx-min-width: 25; -fx-font-size: 13;");

        // Name label
        Label nameLabel = new Label(node.prp.getName());
        nameLabel.setStyle("-fx-font-size: 12;");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        // Status badge
        Label statusLabel = new Label(node.prp.getStatus().getLabel());
        statusLabel.setStyle(
                "-fx-font-size: 9; -fx-padding: 3 8 3 8; -fx-border-radius: 2;" +
                "-fx-background-color: " + node.prp.getStatus().getBackgroundColor() + ";" +
                "-fx-text-fill: " + node.prp.getStatus().getTextColor() + ";"
        );

        // Sub-file count
        int subCount = node.prp.getSubFileCount();
        Label countLabel = null;
        if (subCount > 0) {
            countLabel = new Label("[" + subCount + " sub-files]");
            countLabel.setStyle("-fx-font-size: 9; -fx-text-fill: #999;");
        }

        // Action buttons with keyboard shortcuts
        Button btnCopyPath = new Button("Copy (P)ath");
        Button btnCopyClause = new Button("Copy (C)laude");
        Button btnOpen = new Button("(O)pen");
        Button btnToggle = new Button("(T)oggle");

        btnCopyPath.setStyle("-fx-padding: 4 8 4 8; -fx-font-size: 10; -fx-background-color: #E3F2FD; -fx-text-fill: #1976D2; -fx-border: 1px solid #1976D2;");
        btnCopyClause.setStyle("-fx-padding: 4 8 4 8; -fx-font-size: 10; -fx-background-color: #FFF3E0; -fx-text-fill: #F57C00; -fx-border: 1px solid #F57C00;");
        btnOpen.setStyle("-fx-padding: 4 8 4 8; -fx-font-size: 10; -fx-background-color: #E8F5E9; -fx-text-fill: #388E3C; -fx-border: 1px solid #388E3C;");
        btnToggle.setStyle("-fx-padding: 4 8 4 8; -fx-font-size: 10; -fx-background-color: #F3E5F5; -fx-text-fill: #7B1FA2; -fx-border: 1px solid #7B1FA2;");

        btnCopyPath.setTooltip(new Tooltip("Copy full absolute path"));
        btnCopyClause.setTooltip(new Tooltip("Copy @prp/... format for Claude Code"));
        btnOpen.setTooltip(new Tooltip("Open in editor"));
        btnToggle.setTooltip(new Tooltip("Toggle ACTIVE/CLOSED"));

        btnCopyPath.setOnAction(e -> PrpManagerActions.copyToClipboard(node.prp.getMainFile(), PrpManagerActions.CopyFormat.FULL_PATH));
        btnCopyClause.setOnAction(e -> PrpManagerActions.copyToClipboard(node.prp.getMainFile(), PrpManagerActions.CopyFormat.CLAUDE_PATH));
        btnOpen.setOnAction(e -> PrpManagerActions.openInEditor(node.prp.getMainFile(), settings));
        btnToggle.setOnAction(e -> PrpManagerActions.togglePrpStatus(node.prp));

        if (countLabel != null) {
            rootCell.getChildren().addAll(indexLabel, nameLabel, statusLabel, countLabel, btnCopyPath, btnCopyClause, btnOpen, btnToggle);
        } else {
            rootCell.getChildren().addAll(indexLabel, nameLabel, statusLabel, btnCopyPath, btnCopyClause, btnOpen, btnToggle);
        }

        setText(null);
        setGraphic(rootCell);
    }

    private void updateLeafCell(PrpTreeNode node) {
        HBox leafCell = new HBox(8);
        leafCell.setPadding(new Insets(4, 8, 4, 8));

        Label fileLabel;

        if (node.isMissing) {
            fileLabel = new Label("XX-prp.status.md (missing)");
            fileLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #999; -fx-font-style: italic;");
        } else if (!Files.exists(node.file)) {
            fileLabel = new Label(node.file.getFileName().toString() + " (missing)");
            fileLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #d32f2f;");
        } else {
            fileLabel = new Label(node.file.getFileName().toString());
            fileLabel.setStyle("-fx-font-size: 11;");
        }

        HBox.setHgrow(fileLabel, Priority.ALWAYS);

        // Only add action buttons if file exists
        if (!node.isMissing && Files.exists(node.file)) {
            Button btnCopyPath = new Button("Copy");
            Button btnCopyClause = new Button("Claude");
            Button btnOpen = new Button("Open");

            btnCopyPath.setStyle("-fx-padding: 4 8 4 8; -fx-font-size: 9; -fx-background-color: #E3F2FD; -fx-text-fill: #1976D2; -fx-border: 1px solid #1976D2;");
            btnCopyClause.setStyle("-fx-padding: 4 8 4 8; -fx-font-size: 9; -fx-background-color: #FFF3E0; -fx-text-fill: #F57C00; -fx-border: 1px solid #F57C00;");
            btnOpen.setStyle("-fx-padding: 4 8 4 8; -fx-font-size: 9; -fx-background-color: #E8F5E9; -fx-text-fill: #388E3C; -fx-border: 1px solid #388E3C;");

            btnCopyPath.setTooltip(new Tooltip("Copy full path"));
            btnCopyClause.setTooltip(new Tooltip("Copy @prp/... format"));
            btnOpen.setTooltip(new Tooltip("Open in editor"));

            btnCopyPath.setOnAction(e -> PrpManagerActions.copyToClipboard(node.file, PrpManagerActions.CopyFormat.FULL_PATH));
            btnCopyClause.setOnAction(e -> PrpManagerActions.copyToClipboard(node.file, PrpManagerActions.CopyFormat.CLAUDE_PATH));
            btnOpen.setOnAction(e -> PrpManagerActions.openInEditor(node.file, settings));

            leafCell.getChildren().addAll(fileLabel, btnCopyPath, btnCopyClause, btnOpen);
        } else {
            leafCell.getChildren().add(fileLabel);
        }

        setText(null);
        setGraphic(leafCell);
    }
}
