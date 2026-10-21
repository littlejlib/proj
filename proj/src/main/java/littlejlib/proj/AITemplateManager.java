package littlejlib.proj;

import littlejlib.proj.ui.GlobalPrpManagerDialog;
import littlejlib.proj.ui.PrpManagerDialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AITemplateManager {

    private static final Pattern PRP_PATTERN = Pattern.compile("(\\d{2})-prp-(.+?)\\.md$");
    private static final Pattern CLOSED_PRP_PATTERN = Pattern.compile("(\\d{2})-prp-(.+?)\\.closed\\.md$");

    /**
     * New JavaFX-based PRP manager UI
     */
    public static void manageTemplatesUI(Settings settings) {
        Path prpDir = Paths.get(System.getProperty("user.dir")).resolve("prp");

        if (!Files.exists(prpDir)) {
            try {
                Files.createDirectories(prpDir);
            } catch (IOException e) {
                System.err.println("Error creating prp directory: " + e.getMessage());
                return;
            }
        }

        // Launch JavaFX dialog
        PrpManagerDialog.show(prpDir, settings);
    }

    /**
     * Global PRP viewer UI - shows PRPs from all projects under codeReposPath
     */
    public static void manageGlobalTemplatesUI(Settings settings) {
        if (settings.getCodeReposPath() == null || settings.getCodeReposPath().isEmpty()) {
            System.err.println("Error: Code repos path not configured in settings.");
            return;
        }

        Path codeReposPath = Paths.get(settings.getCodeReposPath());
        if (!Files.exists(codeReposPath)) {
            System.err.println("Error: Code repos path does not exist: " + codeReposPath);
            return;
        }

        // Launch the global PRP viewer dialog
        GlobalPrpManagerDialog.show(settings);
    }

    /**
     * Legacy CLI-based PRP manager (deprecated - use manageTemplatesUI instead)
     */
    @Deprecated
    public static void manageTemplates(Settings settings) {
        while (true) {
            System.out.println("\n--- Project Requirements Prompts (PRPs) Management ---");
            System.out.println("1. Create a (N)ew PRP and open in Editor");
            System.out.println("2. (L)ist existing PRPs");
            System.out.println("3. (V)iew a PRP's Details");
            System.out.println("4. (O)pen a PRP in Editor");
            System.out.println("5. Open a PRP's (S)tatus in Editor");
            System.out.println("6. (C)lose/Reopen PRP");
            System.out.println("0. E(x)it to Main Menu (Esc)");
            System.out.println();
            System.out.flush();

            String choice = readSingleKey();

            switch (choice.toLowerCase()) {
                case "1":
                case "n":
                    createPRP(settings);
                    break;
                case "2":
                case "l":
                    listPRPs();
                    break;
                case "3":
                case "v":
                    viewPRPDetails();
                    break;
                case "4":
                case "o":
                    openPRP(settings);
                    break;
                case "5":
                case "s":
                    openPRPStatus(settings);
                    break;
                case "6":
                case "c":
                    togglePRPStatus();
                    break;
                case "0":
                case "x":
                case "\u001B": // ESC key
                    return;
                default:
                    System.out.println("Invalid option.");
                    System.out.flush();
            }
        }
    }

    /** One keypress. Was JLine raw mode, in three identical copies; now one place. */
    private static String readSingleKey() {
        return Prompt.key();
    }

    private static void createReadme() {
        System.out.println("\n--- Create README.md ---");
        System.out.flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path readmePath = currentDir.resolve("README.md");

        if (Files.exists(readmePath)) {
            String confirm = Prompt.ask("README.md already exists. Overwrite? (y/n): ").trim();
            if (!confirm.equalsIgnoreCase("y")) {
                System.out.println("Cancelled.");
                System.out.flush();
                return;
            }
        }

        String projectName = currentDir.getFileName().toString();
        String readmeContent = "# " + projectName + "\n\n" +
                "## Description\n\n" +
                "<!-- Add project description here -->\n\n" +
                "## Installation\n\n" +
                "<!-- Add installation instructions -->\n\n" +
                "## Usage\n\n" +
                "<!-- Add usage examples -->\n\n" +
                "## License\n\n" +
                "<!-- Add license information -->\n";

        try {
            Files.writeString(readmePath, readmeContent);
            System.out.println("README.md created successfully at: " + readmePath);
            System.out.flush();
        } catch (IOException e) {
            System.out.println("Error creating README.md: " + e.getMessage());
            System.out.flush();
        }
    }

    private static void createPRP(Settings settings) {
        System.out.println("\n--- Create New PRP ---");
        System.out.flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        try {
            if (!Files.exists(prpDir)) {
                Files.createDirectories(prpDir);
                System.out.println("Created prp directory: " + prpDir);
                System.out.flush();
            }

            int nextIndex = getNextPRPIndex(prpDir);
            String indexStr = String.format("%02d", nextIndex);

            String prpName = Prompt.ask("Enter PRP name: ").trim();
            if (prpName.isEmpty()) {
                System.out.println("PRP name cannot be empty.");
                System.out.flush();
                return;
            }

            String sanitizedName = sanitizePRPName(prpName);
            String fileName = indexStr + "-prp-" + sanitizedName + ".md";
            Path prpPath = prpDir.resolve(fileName);

            if (Files.exists(prpPath)) {
                System.out.println("PRP already exists: " + fileName);
                System.out.flush();
                return;
            }

            // Load template using PRPTemplateLoader
            String templateContent;
            try {
                templateContent = PRPTemplateLoader.loadTemplate(settings.getPrpTemplateSrc());
            } catch (IOException e) {
                System.out.println("Warning: Failed to load template from source. Using fallback template.");
                System.out.println("Error: " + e.getMessage());
                System.out.flush();
                templateContent = PRPTemplateLoader.loadTemplate(null); // Use default
            }

            String content = templateContent
                    .replace("%index%", indexStr)
                    .replace("%name%", prpName);

            Files.writeString(prpPath, content);
            System.out.println("PRP created: " + prpPath);
            System.out.flush();

            openInEditor(settings, prpPath);

        } catch (IOException e) {
            System.out.println("Error creating PRP: " + e.getMessage());
            System.out.flush();
        }
    }

    private static void listPRPs() {
        System.out.println("\n--- List PRPs ---");
        System.out.flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            System.out.println("No prp directory found.");
            System.out.flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                System.out.println("No PRPs found.");
                System.out.flush();
                return;
            }

            System.out.println("\nActive PRPs:");
            for (Path prp : prps) {
                String fileName = prp.getFileName().toString();
                Matcher matcher = PRP_PATTERN.matcher(fileName);
                if (matcher.matches()) {
                    String index = matcher.group(1);
                    String name = matcher.group(2);
                    String status = fileName.contains(".closed.") ? "[CLOSED]" : "[ACTIVE]";
                    System.out.println("  " + index + ": " + name + " " + status);
                }
            }
            System.out.println();
            System.out.flush();

        } catch (IOException e) {
            System.out.println("Error listing PRPs: " + e.getMessage());
            System.out.flush();
        }
    }

    private static void openPRP(Settings settings) {
        System.out.println("\n--- Open PRP ---");
        System.out.flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            System.out.println("No prp directory found.");
            System.out.flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                System.out.println("No PRPs found.");
                System.out.flush();
                return;
            }

            System.out.println("\nAvailable PRPs:");
            for (int i = 0; i < prps.size(); i++) {
                System.out.println((i + 1) + ". " + prps.get(i).getFileName().toString());
            }
            System.out.println("0. Cancel");
            System.out.flush();

            String choice = Prompt.ask("Select PRP to open: ").trim();
            if ("0".equals(choice)) {
                return;
            }

            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < prps.size()) {
                Path prpPath = prps.get(index);
                openInEditor(settings, prpPath);
            } else {
                System.out.println("Invalid selection.");
                System.out.flush();
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            System.out.flush();
        } catch (IOException e) {
            System.out.println("Error opening PRP: " + e.getMessage());
            System.out.flush();
        }
    }

    private static void togglePRPStatus() {
        System.out.println("\n--- Close/Reopen PRP ---");
        System.out.flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            System.out.println("No prp directory found.");
            System.out.flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                System.out.println("No PRPs found.");
                System.out.flush();
                return;
            }

            System.out.println("\nAvailable PRPs:");
            for (int i = 0; i < prps.size(); i++) {
                String fileName = prps.get(i).getFileName().toString();
                String status = fileName.contains(".closed.") ? "[CLOSED]" : "[ACTIVE]";
                System.out.println((i + 1) + ". " + fileName + " " + status);
            }
            System.out.println("0. Cancel");
            System.out.flush();

            String choice = Prompt.ask("Select PRP to toggle status: ").trim();
            if ("0".equals(choice)) {
                return;
            }

            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < prps.size()) {
                Path oldPath = prps.get(index);
                String fileName = oldPath.getFileName().toString();
                String newFileName;

                if (fileName.contains(".closed.")) {
                    newFileName = fileName.replace(".closed.", ".");
                    System.out.println("Reopening PRP...");
                } else {
                    newFileName = fileName.replace(".md", ".closed.md");
                    System.out.println("Closing PRP...");
                }

                Path newPath = oldPath.getParent().resolve(newFileName);
                Files.move(oldPath, newPath);
                System.out.println("PRP renamed to: " + newFileName);
                System.out.flush();

            } else {
                System.out.println("Invalid selection.");
                System.out.flush();
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            System.out.flush();
        } catch (IOException e) {
            System.out.println("Error toggling PRP status: " + e.getMessage());
            System.out.flush();
        }
    }

    private static int getNextPRPIndex(Path prpDir) throws IOException {
        if (!Files.exists(prpDir)) {
            return 1;
        }

        List<Integer> indices = Files.list(prpDir)
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(name -> name.matches("\\d{2}-prp-.+\\.md$"))
                .map(name -> {
                    Matcher matcher = Pattern.compile("(\\d{2})-prp-").matcher(name);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                    return 0;
                })
                .collect(Collectors.toList());

        if (indices.isEmpty()) {
            return 1;
        }

        return indices.stream().max(Integer::compareTo).orElse(0) + 1;
    }

    private static void openInEditor(Settings settings, Path filePath) throws IOException {
        String editor = settings.getSingleFileEditor();
        if (editor != null && !editor.isEmpty()) {
            ProcessBuilder pb = new ProcessBuilder(editor, filePath.toString());
            pb.start();
            System.out.println("Opening in editor: " + filePath.getFileName());
            System.out.flush();
        } else {
            System.out.println("Default editor not configured. File created at: " + filePath);
            System.out.flush();
        }
    }

    private static void viewPRPDetails() {
        System.out.println("\n--- View PRP Details ---");
        System.out.flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            System.out.println("No prp directory found.");
            System.out.flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                System.out.println("No PRPs found.");
                System.out.flush();
                return;
            }

            System.out.println("\nAvailable PRPs:");
            for (int i = 0; i < prps.size(); i++) {
                System.out.println((i + 1) + ". " + prps.get(i).getFileName().toString());
            }
            System.out.println("0. Cancel");
            System.out.flush();

            String choice = Prompt.ask("Select PRP to view: ").trim();
            if ("0".equals(choice)) {
                return;
            }

            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < prps.size()) {
                Path prpPath = prps.get(index);
                displayPRPContent(prpPath);

                String prpIndex = extractPRPIndex(prpPath.getFileName().toString());
                Path statusPath = prpDir.resolve(prpIndex + "-prp.status.md");
                if (Files.exists(statusPath)) {
                    System.out.println();
                    displayStatusContent(statusPath);
                } else {
                    System.out.println("\n\t[No status file found for this PRP]");
                    System.out.flush();
                }
            } else {
                System.out.println("Invalid selection.");
                System.out.flush();
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            System.out.flush();
        } catch (IOException e) {
            System.out.println("Error viewing PRP: " + e.getMessage());
            System.out.flush();
        }
    }

    private static void displayPRPContent(Path prpPath) throws IOException {
        System.out.println("\n=== PRP: " + prpPath.getFileName() + " ===");
        System.out.flush();

        List<String> lines = Files.readAllLines(prpPath);
        for (String line : lines) {
            System.out.println("\t" + line);
        }
        System.out.flush();
    }

    private static void displayStatusContent(Path statusPath) throws IOException {
        System.out.println("=== STATUS: " + statusPath.getFileName() + " ===");
        System.out.flush();

        List<String> lines = Files.readAllLines(statusPath);
        for (String line : lines) {
            System.out.println("\t" + line);
        }
        System.out.flush();
    }

    private static void openPRPStatus(Settings settings) {
        System.out.println("\n--- Open PRP Status ---");
        System.out.flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            System.out.println("No prp directory found.");
            System.out.flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                System.out.println("No PRPs found.");
                System.out.flush();
                return;
            }

            System.out.println("\nAvailable PRPs:");
            for (int i = 0; i < prps.size(); i++) {
                String fileName = prps.get(i).getFileName().toString();
                String prpIndex = extractPRPIndex(fileName);
                Path statusPath = prpDir.resolve(prpIndex + "-prp.status.md");
                String statusIndicator = Files.exists(statusPath) ? "" : " [NO STATUS FILE]";
                System.out.println((i + 1) + ". " + fileName + statusIndicator);
            }
            System.out.println("0. Cancel");
            System.out.flush();

            String choice = Prompt.ask("Select PRP status to open: ").trim();
            if ("0".equals(choice)) {
                return;
            }

            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < prps.size()) {
                String prpIndex = extractPRPIndex(prps.get(index).getFileName().toString());
                Path statusPath = prpDir.resolve(prpIndex + "-prp.status.md");

                if (!Files.exists(statusPath)) {
                    System.out.println("Status file does not exist. Create it? (y/n): ");
                    System.out.flush();
                    String create = Prompt.ask().trim();
                    if (create.equalsIgnoreCase("y")) {
                        String template = "PRP Number: " + prpIndex + "\n" +
                                "PRP Name: \n" +
                                "Status: IN PROGRESS\n\n" +
                                "## Implementation Notes\n\n" +
                                "## Completed Tasks\n\n" +
                                "## Pending Tasks\n\n";
                        Files.writeString(statusPath, template);
                        System.out.println("Status file created.");
                        System.out.flush();
                    } else {
                        return;
                    }
                }

                openInEditor(settings, statusPath);
            } else {
                System.out.println("Invalid selection.");
                System.out.flush();
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            System.out.flush();
        } catch (IOException e) {
            System.out.println("Error opening PRP status: " + e.getMessage());
            System.out.flush();
        }
    }

    private static String extractPRPIndex(String fileName) {
        Matcher matcher = Pattern.compile("(\\d{2})-prp-").matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "00";
    }

    /**
     * Sanitizes PRP name according to PRP-09 requirements:
     * 1. Converts to lowercase
     * 2. Removes weird characters (keeps only alphanumeric, spaces, hyphens, underscores)
     * 3. Replaces all spaces with underscores
     *
     * @param prpName the raw PRP name entered by user
     * @return sanitized PRP name suitable for filename
     */
    private static String sanitizePRPName(String prpName) {
        // Step 1: Convert to lowercase
        String sanitized = prpName.toLowerCase();

        // Step 2: Remove weird characters - keep only alphanumeric, spaces, hyphens, and underscores
        sanitized = sanitized.replaceAll("[^a-z0-9\\s_-]", "");

        // Step 3: Replace all spaces (including multiple consecutive spaces) with single underscore
        sanitized = sanitized.replaceAll("\\s+", "_");

        return sanitized;
    }
}
