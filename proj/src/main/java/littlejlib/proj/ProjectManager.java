package littlejlib.proj;

import org.jline.reader.LineReader;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.scanner.ProjectStore;
import littlejlib.proj.scanner.ProjectTreeScanner;
import littlejlib.proj.scanner.ScanProgress;
import littlejlib.proj.ui.ProjectPickerDialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProjectManager {

    /**
     * Opens a project using cached projects from application level.
     * Scans only once per application run (on first call).
     */
    public static void openProject(Terminal terminal, LineReader lineReader, Settings settings,
                                   AtomicReference<List<String>> appCachedProjects) {
        terminal.writer().println("\n--- Open Project ---");
        terminal.writer().flush();
        
        Path codeReposPath = Paths.get(settings.getCodeReposPath());
        if (!Files.exists(codeReposPath) || !Files.isDirectory(codeReposPath)) {
            terminal.writer().println("Code repositories path does not exist: " + codeReposPath);
            terminal.writer().flush();
            return;
        }

        List<String> currentProjects = appCachedProjects.get();

        // First time? Scan and cache at application level
        if (currentProjects == null) {
            // Load from DB cache immediately
            if (Main.isVerbose()) {
                terminal.writer().println("Loading cached projects from database...");
                terminal.writer().flush();
            }
            List<String> dbCachedProjects = relativePaths(new ProjectStore().load(codeReposPath));

            final AtomicReference<List<String>> allProjects = new AtomicReference<>(new ArrayList<>(dbCachedProjects));

            // Start background scan to update DB cache
            Thread scanThread = new Thread(() -> {
                List<String> scanned = rescan(codeReposPath, settings, null);
                allProjects.set(scanned);
                Main.updateCachedProjects(scanned); // Update application cache
            }, "ProjectScanner");
            scanThread.setDaemon(true);
            scanThread.start();

            if (!dbCachedProjects.isEmpty()) {
                if (Main.isVerbose()) {
                    terminal.writer().println(String.format("Loaded %d projects from cache", dbCachedProjects.size()));
                    terminal.writer().println("(Background scan updating database cache...)");
                    terminal.writer().flush();
                }
                // Use cached projects immediately
                currentProjects = dbCachedProjects;
                Main.updateCachedProjects(currentProjects);
            } else {
                if (Main.isVerbose()) {
                    terminal.writer().println("No cache found, scanning directories...");
                    terminal.writer().flush();
                }
                try {
                    scanThread.join(); // Wait for scan if no cache
                    currentProjects = allProjects.get();
                    Main.updateCachedProjects(currentProjects);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } else {
            // Use cached projects from previous call
            if (Main.isVerbose()) {
                terminal.writer().println("Using cached projects from this session...");
                terminal.writer().flush();
            }
        }

        if (currentProjects.isEmpty()) {
            terminal.writer().println("No projects found.");
            terminal.writer().flush();
            return;
        }

        if (Main.isVerbose()) {
            terminal.writer().println("\nOpening project picker UI...");
            terminal.writer().flush();
        }

        // Show JavaFX picker dialog
        var browsePaths = settings.getBrowsePaths().stream().map(Paths::get).filter(Files::isDirectory).toList();
        var result = ProjectPickerDialog.show(browsePaths, settings);

        if (result == null) {
            terminal.writer().println("Selection cancelled.");
            terminal.writer().flush();
            // Exit cleanly - don't try to return to menu
            System.exit(0);
        }

        terminal.writer().println("Selected: " + result.projectPath());
        terminal.writer().flush();

        Path selectedPath = result.absolutePath();

        // Check if action was already executed in GUI
        if (result.action != ProjectPickerDialog.Action.NONE) {
            // Action already executed in GUI (C/F/E pressed)
            // GUI has already called System.exit(0), but just in case:
            terminal.writer().println("Action executed in GUI, exiting...");
            terminal.writer().flush();
            System.exit(0);
        }

        // User pressed Enter in GUI - show TUI action menu
        boolean shouldExit = openProjectOptions(terminal, lineReader, settings, selectedPath);
        if (shouldExit) {
            System.exit(0);
        }
    }

    private static List<String> filterProjects(List<String> projects, String searchText, Terminal terminal) {
        if (searchText.isEmpty()) {
            return projects;
        }

        List<String> matched = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile(searchText, Pattern.CASE_INSENSITIVE);
            for (String project : projects) {
                if (pattern.matcher(project).find()) {
                    matched.add(project);
                }
            }
        } catch (java.util.regex.PatternSyntaxException e) {
            // If regex is invalid, fall back to simple contains search
            terminal.writer().println("(Using simple text search - regex pattern invalid)");
            terminal.writer().flush();
            String lowerSearch = searchText.toLowerCase();
            for (String project : projects) {
                if (project.toLowerCase().contains(lowerSearch)) {
                    matched.add(project);
                }
            }
        }
        return matched;
    }

    /**
     * @deprecated Use openProject() instead which merges both configured and all organizations
     */
    @Deprecated
    public static void openProjectAllOrganizations(Terminal terminal, LineReader lineReader, Settings settings) {
        terminal.writer().println("\n--- Open Project (All Organizations) ---");

        Path codeReposPath = Paths.get(settings.getCodeReposPath());
        if (!Files.exists(codeReposPath) || !Files.isDirectory(codeReposPath)) {
            terminal.writer().println("Code repositories path does not exist: " + codeReposPath);
            terminal.writer().flush();
            return;
        }

        // List all directories in coderepos path as potential organizations
        List<String> allOrganizations = listAllOrganizations(codeReposPath);

        if (allOrganizations.isEmpty()) {
            terminal.writer().println("No organizations found in: " + codeReposPath);
            terminal.writer().flush();
            return;
        }

        // Show organization selection
        terminal.writer().println("\nAll Organizations in " + codeReposPath + ":");
        for (int i = 0; i < allOrganizations.size(); i++) {
            terminal.writer().println((i + 1) + ". " + allOrganizations.get(i));
        }
        terminal.writer().println("0. Cancel");
        terminal.writer().flush();

        String choice = lineReader.readLine("Select organization: ").trim();
        if ("0".equals(choice)) {
            return;
        }

        String selectedOrgName;
        try {
            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < allOrganizations.size()) {
                selectedOrgName = allOrganizations.get(index);
            } else {
                terminal.writer().println("Invalid selection.");
                terminal.writer().flush();
                return;
            }
        } catch (NumberFormatException e) {
            terminal.writer().println("Invalid input.");
            terminal.writer().flush();
            return;
        }

        // Now proceed with project selection from the selected organization
        Path orgPath = codeReposPath.resolve(selectedOrgName);

        // Ask user for sorting preference
        terminal.writer().println("\nSort projects by:");
        terminal.writer().println("1. Alphabetically (A-Z)");
        terminal.writer().println("2. Recently modified (newest first)");
        terminal.writer().println("0. Cancel");
        terminal.writer().flush();

        String sortChoice = lineReader.readLine("Select sorting option: ").trim();
        if ("0".equals(sortChoice)) {
            return;
        }

        boolean sortByRecency = "2".equals(sortChoice);
        List<String> projects = listProjects(orgPath, sortByRecency);

        if (projects.isEmpty()) {
            terminal.writer().println("No projects found in organization: " + selectedOrgName);
            terminal.writer().flush();
            return;
        }

        String sortLabel = sortByRecency ? " (sorted by recency)" : " (sorted alphabetically)";
        terminal.writer().println("\nProjects in " + selectedOrgName + sortLabel + ":");
        for (int i = 0; i < projects.size(); i++) {
            terminal.writer().println((i + 1) + ". " + projects.get(i));
        }
        terminal.writer().println("0. Cancel");
        terminal.writer().flush();

        String projectChoice = lineReader.readLine("Select project: ").trim();
        if ("0".equals(projectChoice)) {
            return;
        }

        try {
            int index = Integer.parseInt(projectChoice) - 1;
            if (index >= 0 && index < projects.size()) {
                String selectedProject = projects.get(index);
                Path projectPath = orgPath.resolve(selectedProject);
                boolean shouldExit = openProjectOptions(terminal, lineReader, settings, projectPath);
                if (shouldExit) {
                    // Exit the entire program
                    System.exit(0);
                }
            } else {
                terminal.writer().println("Invalid selection.");
                terminal.writer().flush();
            }
        } catch (NumberFormatException e) {
            terminal.writer().println("Invalid input.");
            terminal.writer().flush();
        }
    }

    private static boolean openProjectOptions(Terminal terminal, LineReader lineReader, Settings settings, Path projectPath) {
        terminal.writer().println("\n--- Open: " + projectPath.getFileName() + " ---");
        terminal.writer().println("1. (C)hange to project directory in this terminal");
        terminal.writer().println("2. Open in (F)ile Explorer");
        terminal.writer().println("3. Open as Project in Code (E)ditor");
        terminal.writer().println("0. E(x)it to Main Menu (Esc)");
        terminal.writer().flush();

        String choice = readSingleKey(terminal);

        try {
            switch (choice.toLowerCase()) {
                case "1":
                case "c":
                    changeToDirectoryInCurrentTerminal(terminal, projectPath);
                    return true; // Signal to exit the program
                case "2":
                case "f":
                    openInExplorer(projectPath);
                    terminal.writer().println("Opening in file explorer...");
                    terminal.writer().flush();
                    return true; // Exit after opening
                case "3":
                case "e":
                    openInEditor(settings, projectPath);
                    terminal.writer().println("Opening in editor...");
                    terminal.writer().flush();
                    return true; // Exit after opening
                case "0":
                case "x":
                case "\u001B": // ESC key
                    return false;
                default:
                    terminal.writer().println("Invalid option.");
                    terminal.writer().flush();
            }
        } catch (IOException e) {
            terminal.writer().println("Error: " + e.getMessage());
            terminal.writer().flush();
        }
        return false;
    }

    /** For the no-console case: there is no shell to hand a path to, so give them a new one. */
    public static boolean openTerminalAt(Path projectPath) {
        try {
            openInCmd(projectPath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void openInCmd(Path projectPath) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", "cd", "/d", projectPath.toString());
            pb.start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", "-a", "Terminal", projectPath.toString()).start();
        } else {
            ProcessBuilder pb = new ProcessBuilder("x-terminal-emulator");
            pb.directory(projectPath.toFile());
            pb.start();
        }
    }

    private static void openInExplorer(Path projectPath) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            Runtime.getRuntime().exec("explorer.exe \"" + projectPath.toString() + "\"");
        } else if (os.contains("mac")) {
            Runtime.getRuntime().exec("open \"" + projectPath.toString() + "\"");
        } else {
            Runtime.getRuntime().exec("xdg-open \"" + projectPath.toString() + "\"");
        }
    }

    private static void openInEditor(Settings settings, Path projectPath) throws IOException {
        String ideLauncher = settings.getIdeLauncher();
        if (ideLauncher == null || ideLauncher.isEmpty()) {
            throw new IOException("IDE Launcher not configured in settings.");
        }

        // Replace %PATH% placeholder with actual project path
        String command = ideLauncher.replace("%PATH%", projectPath.toString());

        // Parse command into executable and arguments
        String[] parts = parseCommand(command);
        if (parts.length == 0) {
            throw new IOException("Invalid IDE launcher command: " + command);
        }

        ProcessBuilder pb = new ProcessBuilder(parts);
        pb.start();
    }

    /**
     * Parses a command string into executable and arguments.
     * Handles quoted strings properly.
     */
    private static String[] parseCommand(String command) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    private static void changeToDirectoryInCurrentTerminal(Terminal terminal, Path projectPath) {
        terminal.writer().flush();
        Navigate.to(projectPath);
    }

    public static void createNewProject(Terminal terminal, LineReader lineReader, Settings settings) {
        terminal.writer().println("\n--- Create New Project ---");

        Organization selectedOrg = selectOrganization(terminal, lineReader, settings);
        if (selectedOrg == null) {
            return;
        }

        Path orgPath = Paths.get(settings.getCodeReposPath(), selectedOrg.getName());

        terminal.writer().println("\nExisting projects in " + selectedOrg.getName() + ":");
        if (Files.exists(orgPath)) {
            List<String> projects = listProjects(orgPath, false); // Alphabetical for new project creation
            if (!projects.isEmpty()) {
                for (String project : projects) {
                    terminal.writer().print(project + "  ");
                }
                terminal.writer().println();
            } else {
                terminal.writer().println("(No existing projects)");
            }
        } else {
            terminal.writer().println("(Organization directory will be created)");
        }
        terminal.writer().flush();

        String projectName = lineReader.readLine("\nEnter new project name: ").trim();
        if (projectName.isEmpty()) {
            terminal.writer().println("Project name cannot be empty.");
            terminal.writer().flush();
            return;
        }

        String defaultGroupId = selectedOrg.getGroupId() != null ? selectedOrg.getGroupId() : "com.example";
        terminal.writer().println("Suggested Group ID: " + defaultGroupId);
        terminal.writer().flush();

        String groupId = lineReader.readLine("Enter Group ID (press Enter for default): ").trim();
        if (groupId.isEmpty()) {
            groupId = defaultGroupId;
        }

        Path projectPath = orgPath.resolve(projectName);
        if (Files.exists(projectPath)) {
            terminal.writer().println("Project already exists: " + projectPath);
            terminal.writer().flush();
            return;
        }

        try {
            Files.createDirectories(projectPath);
            terminal.writer().println("Project directory created: " + projectPath);
            terminal.writer().println("Group ID: " + groupId);
            terminal.writer().println("Project Name: " + projectName);
            terminal.writer().println("\nNote: You can now use 'Git Clone Repository' or manually initialize the project.");
            terminal.writer().flush();
        } catch (IOException e) {
            terminal.writer().println("Error creating project: " + e.getMessage());
            terminal.writer().flush();
        }
    }

    static Organization selectOrganization(Terminal terminal, LineReader lineReader, Settings settings) {
        List<Organization> organizations = settings.getOrganizations();

        if (organizations.isEmpty()) {
            terminal.writer().println("No organizations configured. Please add organizations in Settings.");
            terminal.writer().flush();
            return null;
        }

        terminal.writer().println("\nSelect Organization:");
        for (int i = 0; i < organizations.size(); i++) {
            terminal.writer().println((i + 1) + ". " + organizations.get(i).getName());
        }
        terminal.writer().println("0. Cancel");
        terminal.writer().flush();

        String choice = lineReader.readLine("Select organization: ").trim();
        if ("0".equals(choice)) {
            return null;
        }

        try {
            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < organizations.size()) {
                return organizations.get(index);
            } else {
                terminal.writer().println("Invalid selection.");
                terminal.writer().flush();
                return null;
            }
        } catch (NumberFormatException e) {
            terminal.writer().println("Invalid input.");
            terminal.writer().flush();
            return null;
        }
    }

    /**
     * Lists all directories in the coderepos path as potential organizations.
     * Returns a sorted list of directory names.
     */
    private static List<String> listAllOrganizations(Path codeReposPath) {
        try {
            return Files.list(codeReposPath)
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted() // Sort alphabetically
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static List<String> listProjects(Path orgPath, boolean sortByRecency) {
        try {
            List<Path> projectPaths = Files.list(orgPath)
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());

            if (sortByRecency) {
                // Sort by last modified time (newest first)
                projectPaths.sort((p1, p2) -> {
                    try {
                        BasicFileAttributes attrs1 = Files.readAttributes(p1, BasicFileAttributes.class);
                        BasicFileAttributes attrs2 = Files.readAttributes(p2, BasicFileAttributes.class);
                        // Compare in reverse order (newest first)
                        return attrs2.lastModifiedTime().compareTo(attrs1.lastModifiedTime());
                    } catch (IOException e) {
                        return 0; // If error reading attributes, consider them equal
                    }
                });
            } else {
                // Sort alphabetically
                projectPaths.sort(Comparator.comparing(p -> p.getFileName().toString()));
            }

            return projectPaths.stream()
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Refreshes the project list by forcing a filesystem scan.
     * Updates both DB cache and application-level cache.
     * Can be called from UI to refresh project list without restarting.
     * @param terminal Terminal for verbose output (can be null for GUI calls)
     * @param settings Application settings
     * @param progressListener Optional listener for progress updates (can be null)
     * @return New list of projects after refresh
     */
    /**
     * Scans a code directory and writes the rows, returning the relative paths.
     * <p>
     * Returning strings is a phase-A shim so the existing ListView picker keeps working; phase B
     * hands the {@link ProjectRecord}s themselves to a TreeTableView and this collapses away.
     */
    public static List<String> rescan(Path codeDir, Settings settings, ScanProgress progress) {
        var scanner = new ProjectTreeScanner(settings.getProjectScanningDepth()).progress(progress);
        var stamp = System.currentTimeMillis();
        var records = scanner.scan(codeDir);
        new ProjectStore().replaceAll(codeDir, records, stamp);
        return relativePaths(records);
    }

    static List<String> relativePaths(List<ProjectRecord> records) {
        return records.stream()
                .map(ProjectRecord::relativePath)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    public static List<String> refreshProjects(Terminal terminal, Settings settings, ScanProgress progressListener) {
        Path codeReposPath = Paths.get(settings.getCodeReposPath());
        if (!Files.exists(codeReposPath) || !Files.isDirectory(codeReposPath)) {
            return new ArrayList<>();
        }

        if (Main.isVerbose() && terminal != null) {
            terminal.writer().println("Refreshing project list...");
            terminal.writer().flush();
        }

        // Force immediate scan (not background)
        List<String> refreshedProjects = rescan(codeReposPath, settings, progressListener);

        // Update application-level cache
        Main.updateCachedProjects(refreshedProjects);

        if (Main.isVerbose() && terminal != null) {
            terminal.writer().println("Project list refreshed.");
            terminal.writer().flush();
        }

        return refreshedProjects;
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
}
