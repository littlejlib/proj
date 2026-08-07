package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import littlejlib.proj.Main;
import littlejlib.proj.Settings;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpScanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PrpManagerDialog {

    private static final Object lockObject = new Object();
    private static Result dialogResult = null;

    /**
     * Main entry point - shows the PRP manager dialog
     * This method blocks until the dialog closes
     */
    public static Result show(Path prpDir, Settings settings) {
        System.out.println("[PrpManagerDialog] Showing PRP manager dialog");
        return JavaFXManager.getInstance().showDialog(
            (lock, resultHolder) -> showOnFxThread(prpDir, settings, lock, resultHolder),
            600000  // 10 minute timeout - user may work with PRPs for a while
        );
    }

    /**
     * The same dialog, opened from code that is ALREADY on the JavaFX thread - the picker.
     * <p>
     * {@link #show} cannot be used from there. It blocks the calling thread on a lock and schedules
     * the dialog through {@code Platform.runLater}, so called from the FX thread the runLater can
     * never execute: the thread that would run it is the one waiting. The result is a frozen window
     * until the timeout expires and then a null. Here the stage is built and shown directly, which
     * is what the FX thread is for.
     */
    public static Result showOnFxThread(Stage owner, Path prpDir, Settings settings) {
        dialogResult = null;
        try {
            java.nio.file.Files.createDirectories(prpDir);
            Stage stage = createDialogStage(prpDir, settings);
            if (owner != null) {
                stage.initOwner(owner);
                stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            }
            stage.showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open " + prpDir + ": " + e.getMessage()).showAndWait();
        }
        return dialogResult != null ? dialogResult : new Result(null, null, null);
    }

    /**
     * Called on JavaFX thread to create and show the dialog.
     * MUST NOT BLOCK - returns immediately after showing dialog.
     */
    private static void showOnFxThread(Path prpDir, Settings settings, Object lock, Object[] resultHolder) throws IOException {
        dialogResult = null;

        Stage stage = createDialogStage(prpDir, settings);

        // Set up close handler to notify when dialog closes
        stage.setOnHidden(event -> {
            System.out.println("[PrpManagerDialog] Dialog closed");
            synchronized (lock) {
                resultHolder[0] = dialogResult != null ? dialogResult : new Result(null, null, null);
                lock.notifyAll();
            }
        });

        stage.show();
        System.out.println("[PrpManagerDialog] Dialog shown successfully (not blocking FX thread)");
    }

    private static Stage createDialogStage(Path prpDir, Settings settings) throws IOException {
        Stage stage = new Stage();
        stage.setTitle("PRP Manager - Project Requirement Prompts");
        stage.setWidth(1000);
        stage.setHeight(700);
        stage.setResizable(true);
        stage.setAlwaysOnTop(false);

        // Create UI components
        VBox root = new VBox(8);
        root.setPadding(new Insets(10));

        // Load initial PRPs
        final List<PrpEntry> allPrps = new ArrayList<>();
        try {
            allPrps.addAll(PrpScanner.scanPrpDirectory(prpDir));
        } catch (IOException e) {
            System.err.println("Error loading PRPs: " + e.getMessage());
        }

        // Search bar
        final TextField searchField = new TextField();
        searchField.setPromptText("Search by index or name (e.g., '17' or 'improve')");
        searchField.setStyle("-fx-padding: 6 10 6 10; -fx-font-size: 12;");

        final Button refreshButton = new Button("Refresh");
        refreshButton.setTooltip(new Tooltip("Rescan PRP directory"));
        refreshButton.setStyle(
                "-fx-padding: 6 15 6 15;" +
                "-fx-background-color: #E3F2FD;" +
                "-fx-text-fill: #1976D2;" +
                "-fx-font-weight: bold;" +
                "-fx-border: 1px solid #1976D2;" +
                "-fx-border-radius: 3;"
        );

        final Button newPrpButton = new Button("+ New PRP");
        newPrpButton.setTooltip(new Tooltip("Create a new PRP (Keyboard: N)"));
        newPrpButton.setStyle(
            "-fx-padding: 6 15 6 15;" +
            "-fx-background-color: #E8F5E9;" +  // Light green background
            "-fx-text-fill: #388E3C;" +          // Dark green text
            "-fx-font-weight: bold;" +
            "-fx-border: 1px solid #388E3C;" +
            "-fx-border-radius: 3;"
        );
        
        // The step this whole window used to end one short of: having written the PRP, the next
        // thing anyone does is copy its path, open a terminal, navigate to the project and paste.
        final Button startAgentButton = new Button("Start agent on PRP");
        startAgentButton.setTooltip(new Tooltip(
                "Open a terminal at the PROJECT root - not the prp folder - running the coding agent "
                        + "with the selected PRP as its instruction"));
        startAgentButton.setStyle(
            "-fx-padding: 6 15 6 15;" +
            "-fx-background-color: #F3E5F5;" +
            "-fx-text-fill: #6A1B9A;" +
            "-fx-font-weight: bold;" +
            "-fx-border: 1px solid #6A1B9A;" +
            "-fx-border-radius: 3;"
        );

        HBox searchBar = new HBox(8);
        searchBar.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");
        searchBar.setPadding(new Insets(8));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchBar.getChildren().addAll(searchField, newPrpButton, startAgentButton, refreshButton);
        root.getChildren().add(searchBar);

        // TreeView for PRPs
        final TreeView<PrpTreeNode> treeView = new TreeView<>();
        treeView.setShowRoot(false);
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // Initial tree setup
        TreeItem<PrpTreeNode> rootItem = createTreeRoot(allPrps);
        treeView.setRoot(rootItem);

        // Apply cell factory
        treeView.setCellFactory(tv -> new PrpTreeCell(settings, prpDir, allPrps));

        root.getChildren().add(treeView);

        // Status bar - shows messages
        final Label statusLabel = new Label("↑↓: Navigate | Space: Expand/Collapse | (P)ath | (C)laude | (O)pen | (T)oggle | (N)ew | (S)tatus | (F)older | Esc: Back");
        statusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666; -fx-padding: 4; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");
        root.getChildren().add(statusLabel);

        Scene scene = new Scene(root);
        stage.setScene(scene);

        // Search field listener
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            List<PrpEntry> filtered = filterPrps(allPrps, newVal);
            TreeItem<PrpTreeNode> newRoot = createTreeRoot(filtered);
            treeView.setRoot(newRoot);

            // Auto-expand first item
            if (!newRoot.getChildren().isEmpty()) {
                newRoot.getChildren().get(0).setExpanded(true);
                treeView.getSelectionModel().select(newRoot.getChildren().get(0));
            }
        });

        // Refresh button
        refreshButton.setOnAction(e -> {
            refreshButton.setDisable(true);
            Thread refreshThread = new Thread(() -> {
                try {
                    List<PrpEntry> newPrps = PrpScanner.scanPrpDirectory(prpDir);
                    Platform.runLater(() -> {
                        allPrps.clear();
                        allPrps.addAll(newPrps);
                        List<PrpEntry> filtered = filterPrps(allPrps, searchField.getText());
                        TreeItem<PrpTreeNode> newRoot = createTreeRoot(filtered);
                        treeView.setRoot(newRoot);
                        refreshButton.setDisable(false);
                    });
                } catch (IOException ex) {
                    Platform.runLater(() -> {
                        refreshButton.setDisable(false);
                    });
                }
            }, "PRP-Refresh");
            refreshThread.setDaemon(true);
            refreshThread.start();
        });
        
        // Add this after the refreshButton.setOnAction() setup:
        newPrpButton.setOnAction(e -> {
            PrpManagerActions.createNewPrp(settings, prpDir);
            statusLabel.setText("✓ Creating new PRP...");

            // Refresh the list after a brief delay to allow file creation
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            refreshButton.fire(); // Trigger refresh to show new PRP
        });

        startAgentButton.setOnAction(e -> {
            TreeItem<PrpTreeNode> selected = treeView.getSelectionModel().getSelectedItem();
            PrpEntry prp = selected == null ? null : owningPrp(selected);
            if (prp == null || prp.getMainFile() == null) {
                statusLabel.setText("Select a PRP first");
                return;
            }
            AgentStarter.start(stage, prp.getMainFile(), settings);
        });

        // Focus on tree
        Platform.runLater(() -> treeView.requestFocus());

        // Keyboard shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            TreeItem<PrpTreeNode> selected = treeView.getSelectionModel().getSelectedItem();

            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN && searchField.isFocused()) {
                treeView.requestFocus();
                if (treeView.getSelectionModel().isEmpty() && treeView.getRoot().getChildren().size() > 0) {
                    treeView.getSelectionModel().select(treeView.getRoot().getChildren().get(0));
                }
                event.consume();
            } else if (selected != null && selected.getValue() != null) {
                PrpTreeNode node = selected.getValue();

                if (event.getCode() == KeyCode.SPACE) {
                    // Expand/Collapse with Space
                    selected.setExpanded(!selected.isExpanded());
                    statusLabel.setText("✓ " + (selected.isExpanded() ? "Expanded" : "Collapsed"));
                    event.consume();
                } else if (event.getCode() == KeyCode.RIGHT && node.isRoot) {
                    selected.setExpanded(true);
                    event.consume();
                } else if (event.getCode() == KeyCode.LEFT && node.isRoot) {
                    selected.setExpanded(false);
                    event.consume();
                } else if (event.getCode() == KeyCode.O) {
                    // (O)pen/Edit
                    if (node.isRoot) {
                        PrpManagerActions.openInEditor(node.prp.getMainFile(), settings);
                        statusLabel.setText("✓ Opening: " + node.prp.getMainFile().getFileName());
                    } else {
                        PrpManagerActions.openInEditor(node.file, settings);
                        statusLabel.setText("✓ Opening: " + node.file.getFileName());
                    }
                    event.consume();
                } else if (event.getCode() == KeyCode.S && node.isRoot) {
                    // (S)tatus file
                    PrpManagerActions.openStatusFile(node.prp, settings, prpDir);
                    statusLabel.setText("✓ Opening status file: " + node.prp.getIndex() + "-prp.status.md");
                    event.consume();
                } else if (event.getCode() == KeyCode.P) {
                    // (P)ath - copy full path
                    Path fileToCopy = node.isRoot ? node.prp.getMainFile() : node.file;
                    PrpManagerActions.copyToClipboard(fileToCopy, PrpManagerActions.CopyFormat.FULL_PATH);
                    statusLabel.setText("✓ Copied Path: " + fileToCopy.getFileName());
                    event.consume();
                } else if (event.getCode() == KeyCode.C) {
                    // (C)laude format
                    Path fileToCopy = node.isRoot ? node.prp.getMainFile() : node.file;
                    PrpManagerActions.copyToClipboard(fileToCopy, PrpManagerActions.CopyFormat.CLAUDE_PATH);
                    statusLabel.setText("✓ Copied Claude: @prp/" + fileToCopy.getFileName());
                    event.consume();
                } else if (event.getCode() == KeyCode.F) {
                    // (F)older
                    PrpManagerActions.openFolder(prpDir);
                    statusLabel.setText("✓ Opening folder in explorer");
                    event.consume();
                } else if (event.getCode() == KeyCode.T && node.isRoot) {
                    // (T)oggle ACTIVE/CLOSED status
                    PrpManagerActions.togglePrpStatus(node.prp);
                    statusLabel.setText("✓ Toggled status to: " + node.prp.getStatus().getLabel());
                    try {
                        Thread.sleep(200); // Brief delay for file system
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    refreshButton.fire();
                    event.consume();
                } else if (event.getCode() == KeyCode.N) {
                    // (N)ew PRP
                    PrpManagerActions.createNewPrp(settings, prpDir);
                    statusLabel.setText("✓ Creating new PRP...");
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    refreshButton.fire();
                    event.consume();
                }
            }
        });

        // Close handler is set up in showOnFxThread() now

        return stage;
    }

    /**
     * The PRP a selected node belongs to, whether the selection is the PRP itself or one of its
     * files. Selecting a status file and being told to "select a PRP first" would be a distinction
     * only this code cares about.
     */
    private static PrpEntry owningPrp(TreeItem<PrpTreeNode> item) {
        for (TreeItem<PrpTreeNode> node = item; node != null; node = node.getParent()) {
            PrpTreeNode value = node.getValue();
            if (value != null && value.prp != null) return value.prp;
        }
        return null;
    }

    private static TreeItem<PrpTreeNode> createTreeRoot(List<PrpEntry> prps) {
        TreeItem<PrpTreeNode> root = new TreeItem<>();

        for (PrpEntry prp : prps) {
            // Create root node for this PRP
            PrpTreeNode prpNode = new PrpTreeNode(prp, null, true);
            TreeItem<PrpTreeNode> prpItem = new TreeItem<>(prpNode);
            prpItem.setExpanded(false);

            // Add main file
            PrpTreeNode mainFileNode = new PrpTreeNode(null, prp.getMainFile(), false);
            prpItem.getChildren().add(new TreeItem<>(mainFileNode));

            // Add status file if exists
            if (prp.hasStatusFile()) {
                PrpTreeNode statusFileNode = new PrpTreeNode(null, prp.getStatusFile(), false);
                prpItem.getChildren().add(new TreeItem<>(statusFileNode));
            } else {
                // Add placeholder for missing status file
                PrpTreeNode missingStatusNode = new PrpTreeNode(null, null, false);
                missingStatusNode.isMissing = true;
                prpItem.getChildren().add(new TreeItem<>(missingStatusNode));
            }

            // Add sub-files
            for (Path subFile : prp.getSubFiles()) {
                PrpTreeNode subFileNode = new PrpTreeNode(null, subFile, false);
                prpItem.getChildren().add(new TreeItem<>(subFileNode));
            }

            root.getChildren().add(prpItem);
        }

        return root;
    }

    /**
     * Helper to filter PRPs by search text
     */
    private static List<PrpEntry> filterPrps(List<PrpEntry> allPrps, String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return allPrps;
        }

        String[] terms = searchText.trim().split("\\s+");
        String pattern = String.join(".*", terms);
        Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

        return allPrps.stream()
                .filter(prp -> {
                    String searchable = prp.getIndex() + " " + prp.getName();
                    return regex.matcher(searchable).find();
                })
                .collect(Collectors.toList());
    }

    /**
     * Data structure for TreeView nodes
     */
    public static class PrpTreeNode {
        public final PrpEntry prp;      // null for file nodes
        public final Path file;         // null for PRP root nodes
        public final boolean isRoot;
        public boolean isMissing = false;

        public PrpTreeNode(PrpEntry prp, Path file, boolean isRoot) {
            this.prp = prp;
            this.file = file;
            this.isRoot = isRoot;
        }

        public String getDisplayText() {
            if (isRoot) {
                int subCount = prp.getSubFileCount();
                String subCountStr = subCount > 0 ? " [" + subCount + " sub-files]" : "";
                return prp.getDisplayName() + " [" + prp.getStatus().getLabel() + "]" + subCountStr;
            } else if (isMissing) {
                return "XX-prp.status.md (not created yet)";
            } else {
                return file.getFileName().toString();
            }
        }

        public boolean isStatusFile() {
            if (file == null) return isMissing;
            return file.getFileName().toString().contains(".status.md");
        }
    }

    /**
     * Result object returned from dialog
     */
    public static class Result {
        public final PrpEntry prp;
        public final Path selectedFile;
        public final String action;

        public Result(PrpEntry prp, Path selectedFile, String action) {
            this.prp = prp;
            this.selectedFile = selectedFile;
            this.action = action;
        }
    }
}
