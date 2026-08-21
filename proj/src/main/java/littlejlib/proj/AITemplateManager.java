package littlejlib.proj;

import org.jline.reader.LineReader;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
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
    public static void manageTemplates(Terminal terminal, LineReader lineReader, Settings settings) {
        while (true) {
            terminal.writer().println("\n--- Project Requirements Prompts (PRPs) Management ---");
            terminal.writer().println("1. Create a (N)ew PRP and open in Editor");
            terminal.writer().println("2. (L)ist existing PRPs");
            terminal.writer().println("3. (V)iew a PRP's Details");
            terminal.writer().println("4. (O)pen a PRP in Editor");
            terminal.writer().println("5. Open a PRP's (S)tatus in Editor");
            terminal.writer().println("6. (C)lose/Reopen PRP");
            terminal.writer().println("0. E(x)it to Main Menu (Esc)");
            terminal.writer().println();
            terminal.writer().flush();

            String choice = readSingleKey(terminal);

            switch (choice.toLowerCase()) {
                case "1":
                case "n":
                    createPRP(terminal, lineReader, settings);
                    break;
                case "2":
                case "l":
                    listPRPs(terminal, lineReader);
                    break;
                case "3":
                case "v":
                    viewPRPDetails(terminal, lineReader);
                    break;
                case "4":
                case "o":
                    openPRP(terminal, lineReader, settings);
                    break;
                case "5":
                case "s":
                    openPRPStatus(terminal, lineReader, settings);
                    break;
                case "6":
                case "c":
                    togglePRPStatus(terminal, lineReader);
                    break;
                case "0":
                case "x":
                case "\u001B": // ESC key
                    return;
                default:
                    terminal.writer().println("Invalid option.");
                    terminal.writer().flush();
            }
        }
    }

    private static String readSingleKey(Terminal terminal) {
        Attributes originalAttributes = null;
        try {
            // Save original attributes before entering raw mode
            originalAttributes = terminal.getAttributes();

            // Enable raw mode to read single characters
            terminal.enterRawMode();
            int c = terminal.reader().read();

            // Handle ESC key
            if (c == 27) {
                return "\u001B";
            }

            return String.valueOf((char) c);
        } catch (IOException e) {
            terminal.writer().println("Error reading input: " + e.getMessage());
            terminal.writer().flush();
            return "";
        } finally {
            // Restore original attributes
            if (originalAttributes != null) {
                try {
                    terminal.setAttributes(originalAttributes);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private static void createReadme(Terminal terminal, LineReader lineReader) {
        terminal.writer().println("\n--- Create README.md ---");
        terminal.writer().flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path readmePath = currentDir.resolve("README.md");

        if (Files.exists(readmePath)) {
            String confirm = lineReader.readLine("README.md already exists. Overwrite? (y/n): ").trim();
            if (!confirm.equalsIgnoreCase("y")) {
                terminal.writer().println("Cancelled.");
                terminal.writer().flush();
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
            terminal.writer().println("README.md created successfully at: " + readmePath);
            terminal.writer().flush();
        } catch (IOException e) {
            terminal.writer().println("Error creating README.md: " + e.getMessage());
            terminal.writer().flush();
        }
    }

    private static void createPRP(Terminal terminal, LineReader lineReader, Settings settings) {
        terminal.writer().println("\n--- Create New PRP ---");
        terminal.writer().flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        try {
            if (!Files.exists(prpDir)) {
                Files.createDirectories(prpDir);
                terminal.writer().println("Created prp directory: " + prpDir);
                terminal.writer().flush();
            }

            int nextIndex = getNextPRPIndex(prpDir);
            String indexStr = String.format("%02d", nextIndex);

            String prpName = lineReader.readLine("Enter PRP name: ").trim();
            if (prpName.isEmpty()) {
                terminal.writer().println("PRP name cannot be empty.");
                terminal.writer().flush();
                return;
            }

            String sanitizedName = sanitizePRPName(prpName);
            String fileName = indexStr + "-prp-" + sanitizedName + ".md";
            Path prpPath = prpDir.resolve(fileName);

            if (Files.exists(prpPath)) {
                terminal.writer().println("PRP already exists: " + fileName);
                terminal.writer().flush();
                return;
            }

            // Load template using PRPTemplateLoader
            String templateContent;
            try {
                templateContent = PRPTemplateLoader.loadTemplate(settings.getPrpTemplateSrc());
            } catch (IOException e) {
                terminal.writer().println("Warning: Failed to load template from source. Using fallback template.");
                terminal.writer().println("Error: " + e.getMessage());
                terminal.writer().flush();
                templateContent = PRPTemplateLoader.loadTemplate(null); // Use default
            }

            String content = templateContent
                    .replace("%index%", indexStr)
                    .replace("%name%", prpName);

            Files.writeString(prpPath, content);
            terminal.writer().println("PRP created: " + prpPath);
            terminal.writer().flush();

            openInEditor(settings, prpPath, terminal);

        } catch (IOException e) {
            terminal.writer().println("Error creating PRP: " + e.getMessage());
            terminal.writer().flush();
        }
    }

    private static void listPRPs(Terminal terminal, LineReader lineReader) {
        terminal.writer().println("\n--- List PRPs ---");
        terminal.writer().flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            terminal.writer().println("No prp directory found.");
            terminal.writer().flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                terminal.writer().println("No PRPs found.");
                terminal.writer().flush();
                return;
            }

            terminal.writer().println("\nActive PRPs:");
            for (Path prp : prps) {
                String fileName = prp.getFileName().toString();
                Matcher matcher = PRP_PATTERN.matcher(fileName);
                if (matcher.matches()) {
                    String index = matcher.group(1);
                    String name = matcher.group(2);
                    String status = fileName.contains(".closed.") ? "[CLOSED]" : "[ACTIVE]";
                    terminal.writer().println("  " + index + ": " + name + " " + status);
                }
            }
            terminal.writer().println();
            terminal.writer().flush();

        } catch (IOException e) {
            terminal.writer().println("Error listing PRPs: " + e.getMessage());
            terminal.writer().flush();
        }
    }

    private static void openPRP(Terminal terminal, LineReader lineReader, Settings settings) {
        terminal.writer().println("\n--- Open PRP ---");
        terminal.writer().flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            terminal.writer().println("No prp directory found.");
            terminal.writer().flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                terminal.writer().println("No PRPs found.");
                terminal.writer().flush();
                return;
            }

            terminal.writer().println("\nAvailable PRPs:");
            for (int i = 0; i < prps.size(); i++) {
                terminal.writer().println((i + 1) + ". " + prps.get(i).getFileName().toString());
            }
            terminal.writer().println("0. Cancel");
            terminal.writer().flush();

            String choice = lineReader.readLine("Select PRP to open: ").trim();
            if ("0".equals(choice)) {
                return;
            }

            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < prps.size()) {
                Path prpPath = prps.get(index);
                openInEditor(settings, prpPath, terminal);
            } else {
                terminal.writer().println("Invalid selection.");
                terminal.writer().flush();
            }

        } catch (NumberFormatException e) {
            terminal.writer().println("Invalid input.");
            terminal.writer().flush();
        } catch (IOException e) {
            terminal.writer().println("Error opening PRP: " + e.getMessage());
            terminal.writer().flush();
        }
    }

    private static void togglePRPStatus(Terminal terminal, LineReader lineReader) {
        terminal.writer().println("\n--- Close/Reopen PRP ---");
        terminal.writer().flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            terminal.writer().println("No prp directory found.");
            terminal.writer().flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                terminal.writer().println("No PRPs found.");
                terminal.writer().flush();
                return;
            }

            terminal.writer().println("\nAvailable PRPs:");
            for (int i = 0; i < prps.size(); i++) {
                String fileName = prps.get(i).getFileName().toString();
                String status = fileName.contains(".closed.") ? "[CLOSED]" : "[ACTIVE]";
                terminal.writer().println((i + 1) + ". " + fileName + " " + status);
            }
            terminal.writer().println("0. Cancel");
            terminal.writer().flush();

            String choice = lineReader.readLine("Select PRP to toggle status: ").trim();
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
                    terminal.writer().println("Reopening PRP...");
                } else {
                    newFileName = fileName.replace(".md", ".closed.md");
                    terminal.writer().println("Closing PRP...");
                }

                Path newPath = oldPath.getParent().resolve(newFileName);
                Files.move(oldPath, newPath);
                terminal.writer().println("PRP renamed to: " + newFileName);
                terminal.writer().flush();

            } else {
                terminal.writer().println("Invalid selection.");
                terminal.writer().flush();
            }

        } catch (NumberFormatException e) {
            terminal.writer().println("Invalid input.");
            terminal.writer().flush();
        } catch (IOException e) {
            terminal.writer().println("Error toggling PRP status: " + e.getMessage());
            terminal.writer().flush();
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

    private static void openInEditor(Settings settings, Path filePath, Terminal terminal) throws IOException {
        String editor = settings.getSingleFileEditor();
        if (editor != null && !editor.isEmpty()) {
            ProcessBuilder pb = new ProcessBuilder(editor, filePath.toString());
            pb.start();
            terminal.writer().println("Opening in editor: " + filePath.getFileName());
            terminal.writer().flush();
        } else {
            terminal.writer().println("Default editor not configured. File created at: " + filePath);
            terminal.writer().flush();
        }
    }

    private static void viewPRPDetails(Terminal terminal, LineReader lineReader) {
        terminal.writer().println("\n--- View PRP Details ---");
        terminal.writer().flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            terminal.writer().println("No prp directory found.");
            terminal.writer().flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                terminal.writer().println("No PRPs found.");
                terminal.writer().flush();
                return;
            }

            terminal.writer().println("\nAvailable PRPs:");
            for (int i = 0; i < prps.size(); i++) {
                terminal.writer().println((i + 1) + ". " + prps.get(i).getFileName().toString());
            }
            terminal.writer().println("0. Cancel");
            terminal.writer().flush();

            String choice = lineReader.readLine("Select PRP to view: ").trim();
            if ("0".equals(choice)) {
                return;
            }

            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < prps.size()) {
                Path prpPath = prps.get(index);
                displayPRPContent(terminal, prpPath);

                String prpIndex = extractPRPIndex(prpPath.getFileName().toString());
                Path statusPath = prpDir.resolve(prpIndex + "-prp.status.md");
                if (Files.exists(statusPath)) {
                    terminal.writer().println();
                    displayStatusContent(terminal, statusPath);
                } else {
                    terminal.writer().println("\n\t[No status file found for this PRP]");
                    terminal.writer().flush();
                }
            } else {
                terminal.writer().println("Invalid selection.");
                terminal.writer().flush();
            }

        } catch (NumberFormatException e) {
            terminal.writer().println("Invalid input.");
            terminal.writer().flush();
        } catch (IOException e) {
            terminal.writer().println("Error viewing PRP: " + e.getMessage());
            terminal.writer().flush();
        }
    }

    private static void displayPRPContent(Terminal terminal, Path prpPath) throws IOException {
        terminal.writer().println("\n=== PRP: " + prpPath.getFileName() + " ===");
        terminal.writer().flush();

        List<String> lines = Files.readAllLines(prpPath);
        for (String line : lines) {
            terminal.writer().println("\t" + line);
        }
        terminal.writer().flush();
    }

    private static void displayStatusContent(Terminal terminal, Path statusPath) throws IOException {
        terminal.writer().println("=== STATUS: " + statusPath.getFileName() + " ===");
        terminal.writer().flush();

        List<String> lines = Files.readAllLines(statusPath);
        for (String line : lines) {
            terminal.writer().println("\t" + line);
        }
        terminal.writer().flush();
    }

    private static void openPRPStatus(Terminal terminal, LineReader lineReader, Settings settings) {
        terminal.writer().println("\n--- Open PRP Status ---");
        terminal.writer().flush();

        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path prpDir = currentDir.resolve("prp");

        if (!Files.exists(prpDir)) {
            terminal.writer().println("No prp directory found.");
            terminal.writer().flush();
            return;
        }

        try {
            List<Path> prps = Files.list(prpDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}-prp-.+\\.md$"))
                    .sorted()
                    .collect(Collectors.toList());

            if (prps.isEmpty()) {
                terminal.writer().println("No PRPs found.");
                terminal.writer().flush();
                return;
            }

            terminal.writer().println("\nAvailable PRPs:");
            for (int i = 0; i < prps.size(); i++) {
                String fileName = prps.get(i).getFileName().toString();
                String prpIndex = extractPRPIndex(fileName);
                Path statusPath = prpDir.resolve(prpIndex + "-prp.status.md");
                String statusIndicator = Files.exists(statusPath) ? "" : " [NO STATUS FILE]";
                terminal.writer().println((i + 1) + ". " + fileName + statusIndicator);
            }
            terminal.writer().println("0. Cancel");
            terminal.writer().flush();

            String choice = lineReader.readLine("Select PRP status to open: ").trim();
            if ("0".equals(choice)) {
                return;
            }

            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < prps.size()) {
                String prpIndex = extractPRPIndex(prps.get(index).getFileName().toString());
                Path statusPath = prpDir.resolve(prpIndex + "-prp.status.md");

                if (!Files.exists(statusPath)) {
                    terminal.writer().println("Status file does not exist. Create it? (y/n): ");
                    terminal.writer().flush();
                    String create = lineReader.readLine().trim();
                    if (create.equalsIgnoreCase("y")) {
                        String template = "PRP Number: " + prpIndex + "\n" +
                                "PRP Name: \n" +
                                "Status: IN PROGRESS\n\n" +
                                "## Implementation Notes\n\n" +
                                "## Completed Tasks\n\n" +
                                "## Pending Tasks\n\n";
                        Files.writeString(statusPath, template);
                        terminal.writer().println("Status file created.");
                        terminal.writer().flush();
                    } else {
                        return;
                    }
                }

                openInEditor(settings, statusPath, terminal);
            } else {
                terminal.writer().println("Invalid selection.");
                terminal.writer().flush();
            }

        } catch (NumberFormatException e) {
            terminal.writer().println("Invalid input.");
            terminal.writer().flush();
        } catch (IOException e) {
            terminal.writer().println("Error opening PRP status: " + e.getMessage());
            terminal.writer().flush();
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
