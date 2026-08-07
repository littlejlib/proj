package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import littlejlib.proj.Settings;
import littlejlib.proj.prp.GlobalPrpEntry;
import littlejlib.proj.prp.GlobalPrpScanner;
import littlejlib.proj.prp.PrpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Global PRP Manager dialog - browse PRPs across all projects.
 * Shows a SplitPane with TreeView on left (grouped by project) and
 * read-only content preview on right.
 * <p>
 * FUNCTION KEYS, sharing {@link PrpManagerDialog}'s numbering wherever the two windows offer the
 * same action, so F2 is open and F4 is copy-path in both. The letters this used to bind were
 * unreachable while typing a search - the guard against that was to ignore every shortcut while the
 * search box had the focus, which made the actions unreachable in the state the window spends most
 * of its life in. A function key needs no such guard, so the guard is gone with the letters.
 */
public class GlobalPrpManagerDialog {

    /**
     * Shows the Global PRP Manager dialog. Blocks until closed.
     */
    public static void show(Settings settings) {
        System.out.println("[GlobalPrpManagerDialog] Showing global PRP manager dialog");
        // Long, for the same reason as the picker: the wait is on a person, and a timeout in the
        // daemon would answer the client while the window was still open.
        JavaFXManager.getInstance().showDialog(
                (lock, resultHolder) -> showOnFxThread(settings, lock, resultHolder),
                900_000
        );
    }

    /**
     * The same dialog, opened from code already running on the JavaFX thread - the picker.
     * <p>
     * {@link #show} blocks the caller on a lock while scheduling the build through
     * {@code Platform.runLater}; from the FX thread that is a deadlock, because the thread that
     * would run the scheduled work is the one waiting for it.
     */
    public static void showOnFxThread(Stage owner, Settings settings) {
        Stage stage = createDialogStage(settings);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        }
        stage.showAndWait();
    }

    private static void showOnFxThread(Settings settings, Object lock, Object[] resultHolder) {
        Stage stage = createDialogStage(settings);

        stage.setOnHidden(event -> {
            System.out.println("[GlobalPrpManagerDialog] Dialog closed");
            synchronized (lock) {
                resultHolder[0] = "CLOSED";
                lock.notifyAll();
            }
        });

        stage.show();
        System.out.println("[GlobalPrpManagerDialog] Dialog shown successfully");
    }

    private static Stage createDialogStage(Settings settings) {
        Stage stage = new Stage();
        stage.setTitle("Global PRP Viewer - All Projects");
        stage.setWidth(1200);
        stage.setHeight(800);
        stage.setResizable(true);

        VBox root = new VBox(8);
        root.setPadding(new Insets(10));

        // All entries container (mutable for refresh)
        final List<GlobalPrpEntry> allEntries = new ArrayList<>();

        // --- Top bar: Search + Status filter + Refresh ---
        final TextField searchField = new TextField();
        searchField.setPromptText("Search by project, PRP name, or content...");
        searchField.setStyle("-fx-padding: 6 10 6 10; -fx-font-size: 12;");

        final ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All", "Active Only", "Closed Only");
        statusFilter.setValue("All");
        statusFilter.setStyle("-fx-font-size: 12;");

        final Button refreshButton = new Button("F12  Refresh");
        refreshButton.setTooltip(new Tooltip("Rescan every project for PRPs   (F12)"));
        refreshButton.setStyle(
                "-fx-padding: 6 15 6 15;" +
                "-fx-background-color: #E3F2FD;" +
                "-fx-text-fill: #1976D2;" +
                "-fx-font-weight: bold;" +
                "-fx-border-radius: 3;"
        );

        HBox topBar = new HBox(8);
        topBar.setPadding(new Insets(8));
        topBar.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        topBar.getChildren().addAll(searchField, statusFilter, refreshButton);
        root.getChildren().add(topBar);

        // --- SplitPane: TreeView (left) + TextArea (right) ---
        final TreeView<GlobalPrpTreeNode> treeView = new TreeView<>();
        treeView.setShowRoot(false);

        final TextArea contentArea = new TextArea();
        contentArea.setEditable(false);
        contentArea.setWrapText(true);
        contentArea.setStyle(
                "-fx-font-family: 'Consolas', 'Courier New', monospace;" +
                "-fx-font-size: 12;"
        );
        contentArea.setText("Select a PRP to view its content.");

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(treeView, contentArea);
        splitPane.setDividerPositions(0.4);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        root.getChildren().add(splitPane);

        // --- Status bar ---
        final Label statusLabel = new Label("Loading PRPs from all projects...");
        statusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666; -fx-padding: 4; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");
        root.getChildren().add(statusLabel);

        Scene scene = new Scene(root);
        stage.setScene(scene);

        // --- Tree cell factory ---
        treeView.setCellFactory(tv -> new GlobalPrpTreeCell(settings));

        // --- TreeView selection -> content preview ---
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                GlobalPrpTreeNode node = newVal.getValue();
                if (node.type == NodeType.PRP_ROOT && node.entry != null) {
                    contentArea.setText("Loading...");
                    Thread contentThread = new Thread(() -> {
                        String content = node.entry.getFullContent();
                        Platform.runLater(() -> contentArea.setText(content));
                    }, "PRP-ContentLoader");
                    contentThread.setDaemon(true);
                    contentThread.start();
                } else if (node.type == NodeType.PROJECT_GROUP) {
                    contentArea.setText("Project: " + node.projectName + "\nPRPs: " + node.prpCount);
                }
            }
        });

        // --- Initial scan on background thread ---
        Runnable doScan = () -> {
            refreshButton.setDisable(true);
            statusLabel.setText("Scanning projects...");

            Thread scanThread = new Thread(() -> {
                Path codeReposPath = Paths.get(settings.getCodeReposPath());
                int maxDepth = settings.getProjectScanningDepth();
                List<GlobalPrpEntry> entries = GlobalPrpScanner.scanAllProjects(codeReposPath, maxDepth);

                Platform.runLater(() -> {
                    allEntries.clear();
                    allEntries.addAll(entries);
                    rebuildTree(treeView, allEntries, searchField.getText(), statusFilter.getValue());
                    int projectCount = countProjects(allEntries);
                    statusLabel.setText(allEntries.size() + " PRPs across " + projectCount + " projects | "
                            + "\u2191\u2193 navigate | Space expand | F2 open | F3 status | F4 path "
                            + "| F5 claude | F6 folder | F12 refresh | Esc close");
                    refreshButton.setDisable(false);
                });
            }, "GlobalPRP-Scan");
            scanThread.setDaemon(true);
            scanThread.start();
        };

        // Run initial scan
        Platform.runLater(doScan);

        // --- Refresh button ---
        refreshButton.setOnAction(e -> doScan.run());

        // --- Search + status filter listeners ---
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            rebuildTree(treeView, allEntries, newVal, statusFilter.getValue());
        });

        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
            rebuildTree(treeView, allEntries, searchField.getText(), newVal);
        });

        // --- Focus tree on load ---
        Platform.runLater(() -> treeView.requestFocus());

        // --- Keyboard shortcuts ---
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            var code = event.getCode();

            if (code == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
                return;
            }
            if (code == KeyCode.F12) {
                refreshButton.fire();
                event.consume();
                return;
            }
            if (code == KeyCode.DOWN && searchField.isFocused()) {
                treeView.requestFocus();
                if (treeView.getSelectionModel().isEmpty() && treeView.getRoot() != null
                        && !treeView.getRoot().getChildren().isEmpty()) {
                    treeView.getSelectionModel().select(treeView.getRoot().getChildren().get(0));
                }
                event.consume();
                return;
            }

            var selected = treeView.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue() == null) return;
            var node = selected.getValue();

            // Navigation stays with the tree, so a space typed into the search box is a space.
            if (treeView.isFocused()) {
                if (code == KeyCode.SPACE) {
                    selected.setExpanded(!selected.isExpanded());
                    event.consume();
                    return;
                }
                if (code == KeyCode.RIGHT && node.type == NodeType.PROJECT_GROUP) {
                    selected.setExpanded(true);
                    event.consume();
                    return;
                }
                if (code == KeyCode.LEFT && node.type == NodeType.PROJECT_GROUP) {
                    selected.setExpanded(false);
                    event.consume();
                    return;
                }
            }

            // A project group row has a prp folder and nothing else, so F6 works there and the
            // rest do not - they act on one PRP, and the group is a heading rather than a PRP.
            if (code == KeyCode.F6) {
                var folder = node.type == NodeType.PRP_ROOT ? node.entry.getPrpDir() : node.prpDirPath;
                if (folder != null) {
                    PrpManagerActions.openFolder(folder);
                    statusLabel.setText("\u2713 Opening folder");
                }
                event.consume();
                return;
            }

            if (node.type != NodeType.PRP_ROOT) return;

            switch (code) {
                case F2 -> {
                    PrpManagerActions.openInEditor(node.entry.getPrpEntry().getMainFile(), settings);
                    statusLabel.setText("\u2713 Opening: " + node.entry.getPrpEntry().getMainFile().getFileName());
                    event.consume();
                }
                case F3 -> {
                    PrpManagerActions.openStatusFile(node.entry.getPrpEntry(), settings, node.entry.getPrpDir());
                    statusLabel.setText("\u2713 Opening status file");
                    event.consume();
                }
                case F4 -> {
                    var filePath = node.entry.getPrpEntry().getMainFile();
                    PrpManagerActions.copyToClipboard(filePath, PrpManagerActions.CopyFormat.FULL_PATH);
                    statusLabel.setText("\u2713 Copied path: " + filePath.getFileName());
                    event.consume();
                }
                case F5 -> {
                    var filePath = node.entry.getPrpEntry().getMainFile();
                    // Relative to the PRP's OWN project root, which is not this window's directory.
                    String claudePath = "@" + node.entry.getProjectPath()
                            .relativize(filePath).toString().replace("\\", "/");
                    javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(claudePath);
                    clipboard.setContent(content);
                    statusLabel.setText("\u2713 Copied Claude: " + claudePath);
                    event.consume();
                }
                default -> { }
            }
        });

        return stage;
    }

    // ===================== Tree building =====================

    private static void rebuildTree(TreeView<GlobalPrpTreeNode> treeView,
                                    List<GlobalPrpEntry> allEntries,
                                    String searchText, String statusFilter) {
        // Filter entries
        List<GlobalPrpEntry> filtered = filterEntries(allEntries, searchText, statusFilter);

        // Group by project name, preserving insertion order (already sorted by lastModified)
        Map<String, List<GlobalPrpEntry>> byProject = new LinkedHashMap<>();
        for (GlobalPrpEntry entry : filtered) {
            byProject.computeIfAbsent(entry.getProjectName(), k -> new ArrayList<>()).add(entry);
        }

        // Sort project groups by the most recently modified PRP within each group
        List<Map.Entry<String, List<GlobalPrpEntry>>> sortedProjects = new ArrayList<>(byProject.entrySet());
        sortedProjects.sort((a, b) -> {
            long aMax = a.getValue().stream().mapToLong(GlobalPrpEntry::getLastModified).max().orElse(0);
            long bMax = b.getValue().stream().mapToLong(GlobalPrpEntry::getLastModified).max().orElse(0);
            return Long.compare(bMax, aMax); // descending
        });

        // Build tree
        TreeItem<GlobalPrpTreeNode> root = new TreeItem<>();

        for (Map.Entry<String, List<GlobalPrpEntry>> projectGroup : sortedProjects) {
            String projectName = projectGroup.getKey();
            List<GlobalPrpEntry> entries = projectGroup.getValue();

            Path prpDirPath = entries.isEmpty() ? null : entries.get(0).getPrpDir();

            GlobalPrpTreeNode groupNode = new GlobalPrpTreeNode(
                    NodeType.PROJECT_GROUP, projectName, entries.size(), null, prpDirPath);
            TreeItem<GlobalPrpTreeNode> groupItem = new TreeItem<>(groupNode);
            groupItem.setExpanded(sortedProjects.size() <= 5); // Auto-expand if few projects

            for (GlobalPrpEntry entry : entries) {
                GlobalPrpTreeNode prpNode = new GlobalPrpTreeNode(
                        NodeType.PRP_ROOT, null, 0, entry, entry.getPrpDir());
                TreeItem<GlobalPrpTreeNode> prpItem = new TreeItem<>(prpNode);
                groupItem.getChildren().add(prpItem);
            }

            root.getChildren().add(groupItem);
        }

        treeView.setRoot(root);

        // Select first PRP node if available
        if (!root.getChildren().isEmpty()) {
            TreeItem<GlobalPrpTreeNode> firstProject = root.getChildren().get(0);
            if (!firstProject.getChildren().isEmpty()) {
                treeView.getSelectionModel().select(firstProject.getChildren().get(0));
            } else {
                treeView.getSelectionModel().select(firstProject);
            }
        }
    }

    private static List<GlobalPrpEntry> filterEntries(List<GlobalPrpEntry> allEntries,
                                                       String searchText, String statusFilter) {
        List<GlobalPrpEntry> result = allEntries;

        // Status filter
        if ("Active Only".equals(statusFilter)) {
            result = result.stream()
                    .filter(e -> e.getPrpEntry().getStatus() == PrpStatus.ACTIVE)
                    .collect(Collectors.toList());
        } else if ("Closed Only".equals(statusFilter)) {
            result = result.stream()
                    .filter(e -> e.getPrpEntry().getStatus() == PrpStatus.CLOSED)
                    .collect(Collectors.toList());
        }

        // Text search
        if (searchText != null && !searchText.trim().isEmpty()) {
            String[] terms = searchText.trim().split("\\s+");
            String pattern = String.join(".*", terms);
            Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

            result = result.stream()
                    .filter(entry -> {
                        String searchable = entry.getProjectName() + " " +
                                entry.getPrpEntry().getIndex() + " " +
                                entry.getPrpEntry().getName();
                        return regex.matcher(searchable).find();
                    })
                    .collect(Collectors.toList());
        }

        return result;
    }

    private static int countProjects(List<GlobalPrpEntry> entries) {
        return (int) entries.stream()
                .map(GlobalPrpEntry::getProjectName)
                .distinct()
                .count();
    }

    // ===================== Tree node data =====================

    enum NodeType {
        PROJECT_GROUP,
        PRP_ROOT
    }

    static class GlobalPrpTreeNode {
        final NodeType type;
        final String projectName;    // for PROJECT_GROUP
        final int prpCount;          // for PROJECT_GROUP
        final GlobalPrpEntry entry;  // for PRP_ROOT
        final Path prpDirPath;       // prp directory path

        GlobalPrpTreeNode(NodeType type, String projectName, int prpCount,
                          GlobalPrpEntry entry, Path prpDirPath) {
            this.type = type;
            this.projectName = projectName;
            this.prpCount = prpCount;
            this.entry = entry;
            this.prpDirPath = prpDirPath;
        }
    }

    // ===================== Tree cell renderer =====================

    private static class GlobalPrpTreeCell extends TreeCell<GlobalPrpTreeNode> {

        private final Settings settings;

        GlobalPrpTreeCell(Settings settings) {
            this.settings = settings;
        }

        @Override
        protected void updateItem(GlobalPrpTreeNode node, boolean empty) {
            super.updateItem(node, empty);

            if (empty || node == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            if (node.type == NodeType.PROJECT_GROUP) {
                updateProjectGroupCell(node);
            } else {
                updatePrpRootCell(node);
            }
        }

        private void updateProjectGroupCell(GlobalPrpTreeNode node) {
            HBox cell = new HBox(8);
            cell.setPadding(new Insets(6, 8, 6, 8));
            cell.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

            Label nameLabel = new Label(node.projectName);
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: #333;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Label countLabel = new Label("(" + node.prpCount + " PRPs)");
            countLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #999;");

            cell.getChildren().addAll(nameLabel, countLabel);

            setText(null);
            setGraphic(cell);
        }

        private void updatePrpRootCell(GlobalPrpTreeNode node) {
            HBox cell = new HBox(8);
            cell.setPadding(new Insets(4, 8, 4, 8));

            // Index
            Label indexLabel = new Label(node.entry.getPrpEntry().getIndex());
            indexLabel.setStyle("-fx-font-weight: bold; -fx-min-width: 25; -fx-font-size: 12;");

            // Name
            Label nameLabel = new Label(node.entry.getPrpEntry().getName());
            nameLabel.setStyle("-fx-font-size: 12;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            // Status badge
            PrpStatus status = node.entry.getPrpEntry().getStatus();
            Label statusLabel = new Label(status.getLabel());
            statusLabel.setStyle(
                    "-fx-font-size: 9; -fx-padding: 2 6 2 6; -fx-border-radius: 2;" +
                    "-fx-background-color: " + status.getBackgroundColor() + ";" +
                    "-fx-text-fill: " + status.getTextColor() + ";"
            );

            // Last modified date
            Label dateLabel = new Label(node.entry.getLastModifiedDisplay());
            dateLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #999;");

            // Action buttons
            Button btnOpen = new Button("Open");
            btnOpen.setStyle("-fx-padding: 3 8 3 8; -fx-font-size: 9; -fx-background-color: #E8F5E9; -fx-text-fill: #388E3C;");
            btnOpen.setOnAction(e -> PrpManagerActions.openInEditor(
                    node.entry.getPrpEntry().getMainFile(), settings));

            Button btnCopyPath = new Button("Path");
            btnCopyPath.setStyle("-fx-padding: 3 8 3 8; -fx-font-size: 9; -fx-background-color: #E3F2FD; -fx-text-fill: #1976D2;");
            btnCopyPath.setOnAction(e -> PrpManagerActions.copyToClipboard(
                    node.entry.getPrpEntry().getMainFile(), PrpManagerActions.CopyFormat.FULL_PATH));

            cell.getChildren().addAll(indexLabel, nameLabel, statusLabel, dateLabel, btnOpen, btnCopyPath);

            setText(null);
            setGraphic(cell);
        }
    }
}
