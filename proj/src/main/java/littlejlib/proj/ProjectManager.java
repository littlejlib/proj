package littlejlib.proj;

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
    public static void openProject(Settings settings,
                                   AtomicReference<List<String>> appCachedProjects) {
        System.out.println("\n--- Open Project ---");
        System.out.flush();
        
        Path codeReposPath = Paths.get(settings.getCodeReposPath());
        if (!Files.exists(codeReposPath) || !Files.isDirectory(codeReposPath)) {
            System.out.println("Code repositories path does not exist: " + codeReposPath);
            System.out.flush();
            return;
        }

        List<String> currentProjects = appCachedProjects.get();

        // First time? Scan and cache at application level
        if (currentProjects == null) {
            // Load from DB cache immediately
            if (Main.isVerbose()) {
                System.out.println("Loading cached projects from database...");
                System.out.flush();
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
                    System.out.println(String.format("Loaded %d projects from cache", dbCachedProjects.size()));
                    System.out.println("(Background scan updating database cache...)");
                    System.out.flush();
                }
                // Use cached projects immediately
                currentProjects = dbCachedProjects;
                Main.updateCachedProjects(currentProjects);
            } else {
                if (Main.isVerbose()) {
                    System.out.println("No cache found, scanning directories...");
                    System.out.flush();
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
                System.out.println("Using cached projects from this session...");
                System.out.flush();
            }
        }

        if (currentProjects.isEmpty()) {
            System.out.println("No projects found.");
            System.out.flush();
            return;
        }

        if (Main.isVerbose()) {
            System.out.println("\nOpening project picker UI...");
            System.out.flush();
        }

        // Show JavaFX picker dialog
        var browsePaths = settings.getBrowsePaths().stream().map(Paths::get).filter(Files::isDirectory).toList();
        var result = ProjectPickerDialog.show(browsePaths, settings);

        if (result == null) {
            System.out.println("Selection cancelled.");
            System.out.flush();
            // Exit cleanly - don't try to return to menu
            System.exit(0);
        }

        System.out.println("Selected: " + result.projectPath());
        System.out.flush();

        Path selectedPath = result.absolutePath();

        // Check if action was already executed in GUI
        if (result.action != ProjectPickerDialog.Action.NONE) {
            // Action already executed in GUI (C/F/E pressed)
            // GUI has already called System.exit(0), but just in case:
            System.out.println("Action executed in GUI, exiting...");
            System.out.flush();
            System.exit(0);
        }

        // User pressed Enter in GUI - show TUI action menu
        boolean shouldExit = openProjectOptions(settings, selectedPath);
        if (shouldExit) {
            System.exit(0);
        }
    }

    private static List<String> filterProjects(List<String> projects, String searchText) {
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
            System.out.println("(Using simple text search - regex pattern invalid)");
            System.out.flush();
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
    public static void openProjectAllOrganizations(Settings settings) {
        System.out.println("\n--- Open Project (All Organizations) ---");

        Path codeReposPath = Paths.get(settings.getCodeReposPath());
        if (!Files.exists(codeReposPath) || !Files.isDirectory(codeReposPath)) {
            System.out.println("Code repositories path does not exist: " + codeReposPath);
            System.out.flush();
            return;
        }

        // List all directories in coderepos path as potential organizations
        List<String> allOrganizations = listAllOrganizations(codeReposPath);

        if (allOrganizations.isEmpty()) {
            System.out.println("No organizations found in: " + codeReposPath);
            System.out.flush();
            return;
        }

        // Show organization selection
        System.out.println("\nAll Organizations in " + codeReposPath + ":");
        for (int i = 0; i < allOrganizations.size(); i++) {
            System.out.println((i + 1) + ". " + allOrganizations.get(i));
        }
        System.out.println("0. Cancel");
        System.out.flush();

        String choice = Prompt.ask("Select organization: ").trim();
        if ("0".equals(choice)) {
            return;
        }

        String selectedOrgName;
        try {
            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < allOrganizations.size()) {
                selectedOrgName = allOrganizations.get(index);
            } else {
                System.out.println("Invalid selection.");
                System.out.flush();
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            System.out.flush();
            return;
        }

        // Now proceed with project selection from the selected organization
        Path orgPath = codeReposPath.resolve(selectedOrgName);

        // Ask user for sorting preference
        System.out.println("\nSort projects by:");
        System.out.println("1. Alphabetically (A-Z)");
        System.out.println("2. Recently modified (newest first)");
        System.out.println("0. Cancel");
        System.out.flush();

        String sortChoice = Prompt.ask("Select sorting option: ").trim();
        if ("0".equals(sortChoice)) {
            return;
        }

        boolean sortByRecency = "2".equals(sortChoice);
        List<String> projects = listProjects(orgPath, sortByRecency);

        if (projects.isEmpty()) {
            System.out.println("No projects found in organization: " + selectedOrgName);
            System.out.flush();
            return;
        }

        String sortLabel = sortByRecency ? " (sorted by recency)" : " (sorted alphabetically)";
        System.out.println("\nProjects in " + selectedOrgName + sortLabel + ":");
        for (int i = 0; i < projects.size(); i++) {
            System.out.println((i + 1) + ". " + projects.get(i));
        }
        System.out.println("0. Cancel");
        System.out.flush();

        String projectChoice = Prompt.ask("Select project: ").trim();
        if ("0".equals(projectChoice)) {
            return;
        }

        try {
            int index = Integer.parseInt(projectChoice) - 1;
            if (index >= 0 && index < projects.size()) {
                String selectedProject = projects.get(index);
                Path projectPath = orgPath.resolve(selectedProject);
                boolean shouldExit = openProjectOptions(settings, projectPath);
                if (shouldExit) {
                    // Exit the entire program
                    System.exit(0);
                }
            } else {
                System.out.println("Invalid selection.");
                System.out.flush();
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            System.out.flush();
        }
    }

    private static boolean openProjectOptions(Settings settings, Path projectPath) {
        System.out.println("\n--- Open: " + projectPath.getFileName() + " ---");
        System.out.println("1. (C)hange to project directory in this terminal");
        System.out.println("2. Open in (F)ile Explorer");
        System.out.println("3. Open as Project in Code (E)ditor");
        System.out.println("0. E(x)it to Main Menu (Esc)");
        System.out.flush();

        String choice = readSingleKey();

        try {
            switch (choice.toLowerCase()) {
                case "1":
                case "c":
                    changeToDirectoryInCurrentTerminal(projectPath);
                    return true; // Signal to exit the program
                case "2":
                case "f":
                    openInExplorer(projectPath);
                    System.out.println("Opening in file explorer...");
                    System.out.flush();
                    return true; // Exit after opening
                case "3":
                case "e":
                    openInEditor(settings, projectPath);
                    System.out.println("Opening in editor...");
                    System.out.flush();
                    return true; // Exit after opening
                case "0":
                case "x":
                case "\u001B": // ESC key
                    return false;
                default:
                    System.out.println("Invalid option.");
                    System.out.flush();
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.flush();
        }
        return false;
    }

    /**
     * For the no-console case: there is no shell to hand a path to, so give them a new one.
     * <p>
     * Delegated rather than done here. This used to spawn {@code cmd.exe /k cd /d} itself, which
     * handed a Command Prompt to somebody who had installed Tabby precisely to stop being handed
     * one - {@link littlejlib.proj.terminal.TerminalRunner} is the one place that knows which
     * terminal the user actually wants.
     */
    public static boolean openTerminalAt(Path projectPath) {
        Settings settings = null;
        try {
            settings = new SettingsManager().loadSettings();
        } catch (Exception e) {
            // no settings is not a reason to refuse a terminal; detection still answers
        }
        return littlejlib.proj.terminal.TerminalRunner.open(projectPath, settings);
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

    private static void changeToDirectoryInCurrentTerminal(Path projectPath) {
        System.out.flush();
        Navigate.to(projectPath);
    }

    public static void createNewProject(Settings settings) {
        System.out.println("\n--- Create New Project ---");

        Organization selectedOrg = selectOrganization(settings);
        if (selectedOrg == null) {
            return;
        }

        Path orgPath = Paths.get(settings.getCodeReposPath(), selectedOrg.getName());

        System.out.println("\nExisting projects in " + selectedOrg.getName() + ":");
        if (Files.exists(orgPath)) {
            List<String> projects = listProjects(orgPath, false); // Alphabetical for new project creation
            if (!projects.isEmpty()) {
                for (String project : projects) {
                    System.out.print(project + "  ");
                }
                System.out.println();
            } else {
                System.out.println("(No existing projects)");
            }
        } else {
            System.out.println("(Organization directory will be created)");
        }
        System.out.flush();

        String projectName = Prompt.ask("\nEnter new project name: ").trim();
        if (projectName.isEmpty()) {
            System.out.println("Project name cannot be empty.");
            System.out.flush();
            return;
        }

        String defaultGroupId = selectedOrg.getGroupId() != null ? selectedOrg.getGroupId() : "com.example";
        System.out.println("Suggested Group ID: " + defaultGroupId);
        System.out.flush();

        String groupId = Prompt.ask("Enter Group ID (press Enter for default): ").trim();
        if (groupId.isEmpty()) {
            groupId = defaultGroupId;
        }

        Path projectPath = orgPath.resolve(projectName);
        if (Files.exists(projectPath)) {
            System.out.println("Project already exists: " + projectPath);
            System.out.flush();
            return;
        }

        try {
            Files.createDirectories(projectPath);
            System.out.println("Project directory created: " + projectPath);
            System.out.println("Group ID: " + groupId);
            System.out.println("Project Name: " + projectName);
            System.out.println("\nNote: You can now use 'Git Clone Repository' or manually initialize the project.");
            System.out.flush();
        } catch (IOException e) {
            System.out.println("Error creating project: " + e.getMessage());
            System.out.flush();
        }
    }

    static Organization selectOrganization(Settings settings) {
        List<Organization> organizations = settings.getOrganizations();

        if (organizations.isEmpty()) {
            System.out.println("No organizations configured. Please add organizations in Settings.");
            System.out.flush();
            return null;
        }

        System.out.println("\nSelect Organization:");
        for (int i = 0; i < organizations.size(); i++) {
            System.out.println((i + 1) + ". " + organizations.get(i).getName());
        }
        System.out.println("0. Cancel");
        System.out.flush();

        String choice = Prompt.ask("Select organization: ").trim();
        if ("0".equals(choice)) {
            return null;
        }

        try {
            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < organizations.size()) {
                return organizations.get(index);
            } else {
                System.out.println("Invalid selection.");
                System.out.flush();
                return null;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            System.out.flush();
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

    public static List<String> refreshProjects(Settings settings, ScanProgress progressListener) {
        Path codeReposPath = Paths.get(settings.getCodeReposPath());
        if (!Files.exists(codeReposPath) || !Files.isDirectory(codeReposPath)) {
            return new ArrayList<>();
        }

        if (Main.isVerbose()) {
            System.out.println("Refreshing project list...");
            System.out.flush();
        }

        // Force immediate scan (not background)
        List<String> refreshedProjects = rescan(codeReposPath, settings, progressListener);

        // Update application-level cache
        Main.updateCachedProjects(refreshedProjects);

        if (Main.isVerbose()) {
            System.out.println("Project list refreshed.");
            System.out.flush();
        }

        return refreshedProjects;
    }

    /** One keypress. Was JLine raw mode, in three identical copies; now one place. */
    private static String readSingleKey() {
        return Prompt.key();
    }
}
