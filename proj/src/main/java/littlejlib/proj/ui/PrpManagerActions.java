package littlejlib.proj.ui;

import javafx.application.Platform;
import littlejlib.proj.PRPTemplateLoader;
import littlejlib.proj.Settings;
import littlejlib.proj.prp.PrpEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class PrpManagerActions {

    /**
     * Opens a file in the configured editor
     */
    public static void openInEditor(Path filePath, Settings settings) {
        if (!Files.exists(filePath)) {
            showNotification("File not found: " + filePath.getFileName());
            return;
        }

        String editor = settings != null ? settings.getSingleFileEditor() : null;

        if (editor == null || editor.isEmpty()) {
            // Fallback to Desktop API
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                    if (desktop.isSupported(java.awt.Desktop.Action.EDIT)) {
                        desktop.edit(filePath.toFile());
                        showNotification("Opened in default editor");
                        return;
                    }
                }
            } catch (IOException e) {
                showNotification("Error: Editor not configured");
                return;
            }
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(editor, filePath.toAbsolutePath().toString());
            pb.start();
            showNotification("Opened: " + filePath.getFileName());
        } catch (IOException e) {
            showNotification("Error opening file: " + e.getMessage());
        }
    }

    /**
     * Opens the PRP folder in file explorer
     */
    public static void openFolder(Path folderPath) {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;

        try {
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", folderPath.toAbsolutePath().toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", folderPath.toAbsolutePath().toString());
            } else {
                // Linux
                pb = new ProcessBuilder("xdg-open", folderPath.toAbsolutePath().toString());
            }
            pb.start();
            showNotification("Opened folder");
        } catch (IOException e) {
            showNotification("Error: " + e.getMessage());
        }
    }

    /**
     * Toggles PRP status between ACTIVE and CLOSED
     */
    public static void togglePrpStatus(PrpEntry prp) {
        Path oldPath = prp.getMainFile();
        String oldName = oldPath.getFileName().toString();
        String newName;

        if (prp.getStatus().toString().equals("CLOSED")) {
            // Reopen: remove .closed
            newName = oldName.replace(".closed.", ".");
        } else {
            // Close: add .closed
            newName = oldName.replace(".md", ".closed.md");
        }

        Path newPath = oldPath.getParent().resolve(newName);

        try {
            Files.move(oldPath, newPath);
            showNotification("PRP status toggled");
        } catch (IOException e) {
            showNotification("Error: " + e.getMessage());
        }
    }

    /**
     * Copies file path to system clipboard
     */
    public static void copyToClipboard(Path filePath, CopyFormat format) {
        String textToCopy = formatPath(filePath, format);

        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(textToCopy);
        clipboard.setContent(content);

        showNotification("Copied: " + format.getLabel());
    }

    /**
     * Formats a path according to the specified format.
     * <p>
     * The relative forms are GONE, and deliberately. They were computed against
     * {@code user.dir} - the process working directory, which for a daemon is wherever it happened
     * to be started and is almost never the project whose PRP is on screen. So {@code @prp/...} came
     * out relative to the wrong root: a path that looks entirely plausible and that the agent cannot
     * open. {@link PrpActions#claudePath} does it against the PRP's own project, which is the only
     * root that means anything.
     */
    public static String formatPath(Path filePath, CopyFormat format) {
        return format == CopyFormat.FILE_NAME
                ? filePath.getFileName().toString() : filePath.toAbsolutePath().toString();
    }

    /**
     * Shows a brief notification message
     */
    private static void showNotification(String message) {
        System.out.println("[PRP] " + message);
    }

    /**
     * Opens or creates the status file for a PRP
     */
    public static void openStatusFile(PrpEntry prp, Settings settings, Path prpDir) {
        Path statusPath = prpDir.resolve(prp.getIndex() + "-prp.status.md");

        // Create if missing
        if (!Files.exists(statusPath)) {
            try {
                String template = "PRP Number: " + prp.getIndex() + "\n" +
                        "PRP Name: " + prp.getName() + "\n" +
                        "Status: IN PROGRESS\n\n" +
                        "## Implementation Notes\n\n" +
                        "## Completed Tasks\n\n" +
                        "## Pending Tasks\n\n";
                Files.writeString(statusPath, template);
                showNotification("Created status file");
            } catch (IOException e) {
                showNotification("Error creating status file: " + e.getMessage());
                return;
            }
        }

        openInEditor(statusPath, settings);
    }

    /**
     * Creates a new PRP with user input
     */
    public static void createNewPrp(Settings settings, Path prpDir) {
        // Use a simple text input dialog
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("");
        dialog.setTitle("Create New PRP");
        dialog.setHeaderText("Enter PRP Name");
        dialog.setContentText("PRP name:");

        java.util.Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String prpName = result.get().trim();
            if (prpName.isEmpty()) {
                showNotification("PRP name cannot be empty");
                return;
            }

            try {
                // The rules live in PrpCreator so that the command line creates PRPs the same way
                // this window does - one implementation, not two that drift.
                var created = littlejlib.proj.prp.PrpCreator
                        .create(prpDir, prpName, null, settings);
                showNotification("Created: " + created.file().getFileName());
                openInEditor(created.file(), settings);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                showNotification("PRP already exists: " + Path.of(e.getFile()).getFileName());
            } catch (IOException | IllegalArgumentException e) {
                showNotification("Error creating PRP: " + e.getMessage());
            }
        }
    }

    /**
     * Displays PRP details (can be enhanced with a preview dialog)
     */
    public static void viewPrpDetails(PrpEntry prp) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("PRP Details");
        alert.setHeaderText(prp.getDisplayName());

        StringBuilder content = new StringBuilder();
        content.append("Status: ").append(prp.getStatus()).append("\n");
        content.append("Main File: ").append(prp.getMainFile().getFileName()).append("\n");
        content.append("Has Status File: ").append(prp.hasStatusFile()).append("\n");
        content.append("Sub-files: ").append(prp.getSubFileCount()).append("\n\n");

        if (prp.getSubFileCount() > 0) {
            content.append("Sub-files:\n");
            for (Path subFile : prp.getSubFiles()) {
                content.append("  - ").append(subFile.getFileName()).append("\n");
            }
        }

        alert.setContentText(content.toString());
        alert.showAndWait();
    }

    /**
     * Gets the next available PRP index
     */
    private static int getNextPRPIndex(Path prpDir) throws IOException {
        if (!Files.exists(prpDir)) {
            return 1;
        }

        java.util.List<Integer> indices = Files.list(prpDir)
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(name -> name.matches("\\d{2}-prp-.+\\.md$"))
                .map(name -> {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2})-prp-").matcher(name);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                    return 0;
                })
                .collect(java.util.stream.Collectors.toList());

        if (indices.isEmpty()) {
            return 1;
        }

        return indices.stream().max(Integer::compareTo).orElse(0) + 1;
    }

    /**
     * Sanitizes PRP name for use in filename
     */
    private static String sanitizePRPName(String prpName) {
        // Convert to lowercase
        String sanitized = prpName.toLowerCase();

        // Keep only alphanumeric, spaces, hyphens, and underscores
        sanitized = sanitized.replaceAll("[^a-z0-9\\s_-]", "");

        // Replace spaces with underscores
        sanitized = sanitized.replaceAll("\\s+", "_");

        return sanitized;
    }

    /**
     * Copy format enum - duplicate here for convenience
     */
    public enum CopyFormat {
        FULL_PATH("Full Path"),
        FILE_NAME("File Name");

        private final String label;

        CopyFormat(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
