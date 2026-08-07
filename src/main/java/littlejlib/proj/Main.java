package littlejlib.proj;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import littlejlib.proj.ui.MainMenuDialog;

import java.io.IOException;

@Command(name = "prj",
         description = "Project Build & Dev Tools - Command-line tool for managing development projects",
         version = "1.0",
         mixinStandardHelpOptions = false,
         subcommands = {
             Main.OpenCommand.class,
             Main.NewCommand.class,
             littlejlib.proj.cli.CloneCommands.CloneCmd.class,
             Main.PrpCommand.class,
             Main.GlobalPrpCommand.class,
             Main.SettingsCommand.class,
             Main.IdeCommand.class,
             littlejlib.proj.daemon.DaemonCommand.class,
             RepoBaseCommand.class,
             Main.ListCommand.class,
             Main.FindCommand.class,
             Main.InfoCommand.class,
             Main.DepsCommand.class,
             Main.CentralCommand.class,
             Main.ScanCommand.class,
             Main.TermCommand.class,
             Main.FilesCommand.class,
             Main.WhereCommand.class,
             Main.ImpactCommand.class,
             Main.BlockedCommand.class,
             Main.ConfigCommand.class,
             littlejlib.proj.cli.ShellCommands.ShellCmd.class,
             littlejlib.proj.cli.AotCommands.AotCmd.class,
             Main.HelpCommand.class
         },
         footer = "%nRun 'prj' without arguments to use interactive menu mode.%n")
public class Main implements Runnable {
    @picocli.CommandLine.Option(names = {"--verbose", "-v", "--debug"}, description = "Enable verbose/debug output")
    private boolean verboseOpt = false;

    private static Settings settings;
    private static SettingsManager settingsManager;
    private static Terminal terminal;
    private static LineReader lineReader;

    // Cache scanned projects at application level (scan once per run)
    private static java.util.List<String> cachedProjects = null;
    private static boolean scanInProgress = false;

    // Verbose mode flag
    private static boolean verboseMode = false;

    private static boolean verboseInstanceOpt = false;

    public static boolean isVerbose() {
        return verboseMode || verboseInstanceOpt;
    }

    /**
     * When jr says this run is the one writing the AOT cache, load the application heavily first
     * so the cache is trained on the whole app rather than on whatever the user happened to type.
     * <p>
     * The user's command still runs afterwards, in this same process — the JVM assembles the cache
     * at exit, so one run can both train and do the work. Rejecting the command instead, or
     * re-executing it in a second process, would both be worse: this costs one slow run, once per
     * rebuild, and nothing is refused.
     */
    static void warmUpIfBuildingCache() {
        try {
            if (!littlejlib.proj.aot.AotState.creating()) return;
            var err = LogConfig.err();
            err.println("[proj] first run since the jar changed - warming up to build the startup");
            err.println("[proj] cache. This run is slower; the next ones are much faster.");
            littlejlib.proj.aot.AotWarmup.run(err);
        } catch (Throwable t) {
            // A warmup that fails must never take the user's command down with it.
        }
    }

    static void enableUnicodeConsole() {
        try {
            xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput.enable();
        } catch (Throwable t) {
            // Not Windows, or no console attached. Nothing to do and nothing worth saying.
        }
    }

    public static void setVerbose(boolean verbose) {
        verboseMode = verbose;
        verboseInstanceOpt = verbose;
    }

    public static void main(String[] args) {
        // Before anything writes a byte. protect() moves System.out to stderr so stdout
        // is left carrying only the resolved path, which is what lets a shell adapter
        // wrap this process in `for /f` and read a clean answer.
        StdoutChannel.protect();
        // Replaces the `chcp 65001` that proj.bat used to do; a batch wrapper is no
        // longer in the picture to do it for us.
        enableUnicodeConsole();

        // --init writes an adapter to stdout and exits. Handled here because it must not
        // build a terminal, load settings, or emit anything else on stdout.
        if (args.length > 0 && "--init".equals(args[0])) {
            System.exit(ShellInit.emit(args.length > 1 ? args[1] : null));
        }

        // The adapter passes --print-cd, which tells Navigate that something is reading
        // stdout, so it should not also nag or touch the clipboard.
        var forwarded = args;
        if (args.length > 0 && "--print-cd".equals(args[0])) {
            Navigate.adapterReading(true);
            forwarded = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        args = forwarded;

        // Initialize logging configuration FIRST (before any library loads)
        // Default to false, will be updated if Picocli sets it
        LogConfig.initialize(false);

        // The training payload, when jr launched us with -XX:AOTCacheOutput. Handled before the
        // terminal and settings so nothing else colours the run.
        if (args.length > 0 && littlejlib.proj.aot.AotBuilder.WARMUP_FLAG.equals(args[0])) {
            littlejlib.proj.aot.AotWarmup.run(LogConfig.err());
            System.exit(0);
        }

        warmUpIfBuildingCache();

        // The resident half. Handled before the terminal is built: a daemon has no tty.
        if (args.length > 0 && "--daemon".equals(args[0])) {
            try {
                new littlejlib.proj.daemon.Server().run();
            } catch (Exception e) {
                System.err.println("daemon failed to start: " + e.getMessage());
                e.printStackTrace();
                System.exit(4);
            }
            return;
        }

        // "proj o" through the daemon needs no terminal, no picocli and no settings parse in this
        // process. Building a JLine system terminal alone is a measurable slice of a startup whose
        // whole selling point is being quick.
        if (args.length == 1 && ("o".equals(args[0]) || "open".equals(args[0]))) {
            var exitCode = littlejlib.proj.daemon.OpenViaDaemon.open(isVerbose());
            if (exitCode != null) System.exit(exitCode);
        }

        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();

            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            settingsManager = new SettingsManager();
            settings = settingsManager.loadSettings();

            // Handle first run setup
            if (settings == null) {
                handleFirstRun();
                System.exit(0);
            }

            // Check if CLI arguments provided
            if (args.length == 0) {
                // No arguments - show JavaFX interactive menu
                mainMenu();
            } else {
                // CLI mode - use Picocli to parse and execute command
                Main mainCmd = new Main();
                CommandLine cmd = new CommandLine(mainCmd);

                // Execute command (this will parse and set verboseOpt)
                int exitCode = cmd.execute(args);

                // After execution, update verbose flag if it was set
                if (mainCmd.verboseOpt) {
                    setVerbose(true);
                }

                System.exit(exitCode);
            }

        } catch (IOException e) {
            System.err.println("Error initializing terminal: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }

    /**
     * Implements Runnable - called when main command is invoked without subcommands
     */
    @Override
    public void run() {
        // Update verbose flag from command option
        if (verboseOpt) {
            setVerbose(true);
            System.out.println("[DEBUG] Verbose mode enabled");
        }

        // When 'prj' is called without subcommands in CLI mode, show JavaFX interactive menu
        try {
            mainMenu();
        } catch (Exception e) {
            System.err.println("Error running interactive menu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleFirstRun() {
        printBanner();
        terminal.writer().println("FIRST RUN DETECTED");
        terminal.writer().println();
        terminal.writer().println("A template Settings.toml file has been created at:");
        terminal.writer().println("  " + settingsManager.getSettingsFilePath());
        terminal.writer().println();
        terminal.writer().println("Please edit this file to configure:");
        terminal.writer().println("  - Your code repositories path");
        terminal.writer().println("  - Default editor");
        terminal.writer().println("  - Organizations with their groupIds");
        terminal.writer().println("  - Other preferences");
        terminal.writer().println();
        terminal.writer().println("After editing the file, run this program again.");
        terminal.writer().flush();

        try {
            String openNow = lineReader.readLine("Open settings file now? (y/n): ").trim();
            if (openNow.equalsIgnoreCase("y")) {
                openFileWithFallback(settingsManager.getSettingsFilePath().toFile());
            }
        } catch (Exception e) {
            // Ignore - just exit
        }
    }

    private static void printBanner() {
        terminal.writer().println("========================================");
        terminal.writer().println("  Project Build & Dev Tools");
        terminal.writer().println("  Version 1.0");
        terminal.writer().println("========================================");
        terminal.writer().println();
        terminal.writer().flush();
    }

    private static void mainMenu() {
        // Show main menu - show once, sub-actions don't loop back
        MainMenuDialog.Action action = MainMenuDialog.show(settings);

        switch (action) {
            case OPEN_PROJECT:
                ProjectManager.openProject(terminal, lineReader, settings, getCachedProjects());
                break;
            case GIT_CLONE:
                GitManager.cloneRepository(terminal, lineReader, settings);
                break;
            case NEW_PROJECT:
                ProjectManager.createNewProject(terminal, lineReader, settings);
                break;
            case PRPS_MANAGER:
                AITemplateManager.manageTemplatesUI(settings);
                break;
            case GLOBAL_PRPS_MANAGER:
                AITemplateManager.manageGlobalTemplatesUI(settings);
                break;
            case SETTINGS:
                settingsMenu();
                break;
            case IDE_EDITOR:
                openCurrentDirInIDE();
                break;
            case BACK:
            case EXIT:
            default:
                // Any exit action (BACK from menu, EXIT, or null/default)
                terminal.writer().println("\nGoodbye!");
                terminal.writer().flush();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.exit(0);
        }
    }

    private static String readSingleKey() {
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

    private static void settingsMenu() {
        while (true) {
            terminal.writer().println("\n--- Settings ---");
            terminal.writer().println("Current Settings:");
            terminal.writer().println("  Code Repos Path: " + settings.getCodeReposPath());
            String mirrorPath = settings.getGitHubMirrorPath();
            terminal.writer().println("  GitHub Mirror Path: " + (mirrorPath == null || mirrorPath.isEmpty()
                    ? "(not set - clones go to Code Repos Path)" : mirrorPath));
            terminal.writer().println("  Single File Editor: " + settings.getSingleFileEditor());
            terminal.writer().println("  IDE Launcher: " + settings.getIdeLauncher());
            terminal.writer().print("  Organizations: ");
            if (settings.getOrganizations().isEmpty()) {
                terminal.writer().println("(none)");
            } else {
                for (Organization org : settings.getOrganizations()) {
                    terminal.writer().print(org.getName() + " ");
                }
                terminal.writer().println();
            }
            terminal.writer().println();
            terminal.writer().println("1. Configure (C)ode Repository Path");
            terminal.writer().println("2. Configure (G)itHub Mirror Path");
            terminal.writer().println("3. Configure Project Scanning (D)epth");
            terminal.writer().println("4. Manage (O)rganizations");
            terminal.writer().println("5. Configure Single File (E)ditor");
            terminal.writer().println("6. Configure Project IDE (L)auncher");
            terminal.writer().println("7. Edit Settings File (R)aw");
            terminal.writer().println("8. Show Settings (F)ile Location");
            terminal.writer().println("9. Re(l)oad Settings from File");
            terminal.writer().println("0. E(x)it to Main Menu (Esc)");
            terminal.writer().println();
            terminal.writer().flush();

            String choice = readSingleKey();

            switch (choice.toLowerCase()) {
                case "1":
                case "c":
                    String newPath = lineReader.readLine("Enter new Code Repos Path: ");
                    settings.setCodeReposPath(newPath);
                    settingsManager.saveSettings(settings);
                    terminal.writer().println("Settings saved.");
                    terminal.writer().flush();
                    break;
                case "2":
                case "g":
                    terminal.writer().println("\nSeparate directory for arbitrary cloned GitHub repos.");
                    terminal.writer().println("Leave empty to clone into the Code Repos Path instead.");
                    terminal.writer().flush();
                    String newMirror = lineReader.readLine("Enter GitHub Mirror Path: ").trim();
                    settings.setGitHubMirrorPath(newMirror);
                    settingsManager.saveSettings(settings);
                    terminal.writer().println("Settings saved.");
                    terminal.writer().flush();
                    break;
                case "3":
                case "d":
                    String depthStr = lineReader.readLine("Enter project scanning depth (current: " + settings.getProjectScanningDepth() + "): ");
                    try {
                        int depth = Integer.parseInt(depthStr.trim());
                        settings.setProjectScanningDepth(depth);
                        settingsManager.saveSettings(settings);
                        terminal.writer().println("Settings saved.");
                    } catch (NumberFormatException e) {
                        terminal.writer().println("Invalid number. Settings not changed.");
                    }
                    terminal.writer().flush();
                    break;
                case "4":
                case "o":
                    manageOrganizations();
                    break;
                case "5":
                case "e":
                    String newEditor = lineReader.readLine("Enter Single File Editor (full path): ");
                    settings.setSingleFileEditor(newEditor);
                    settingsManager.saveSettings(settings);
                    terminal.writer().println("Settings saved.");
                    terminal.writer().flush();
                    break;
                case "6":
                case "l":
                    terminal.writer().println("\nEnter IDE Launcher command template.");
                    terminal.writer().println("Use %PATH% as placeholder for project path.");
                    terminal.writer().println("Example: C:\\Program Files\\NetBeans\\bin\\netbeans.exe --open \"%PATH%\"");
                    terminal.writer().flush();
                    String newLauncher = lineReader.readLine("IDE Launcher: ");
                    settings.setIdeLauncher(newLauncher);
                    settingsManager.saveSettings(settings);
                    terminal.writer().println("Settings saved.");
                    terminal.writer().flush();
                    break;
                case "7":
                case "r":
                    openSettingsFileInEditor();
                    break;
                case "8":
                case "f":
                    terminal.writer().println("Settings file: " + settingsManager.getSettingsFilePath());
                    terminal.writer().flush();
                    break;
                case "9":
                    settings = settingsManager.loadSettings();
                    if (settings == null) {
                        terminal.writer().println("Error reloading settings. Using previous settings.");
                        settings = settingsManager.loadSettings();
                    } else {
                        terminal.writer().println("Settings reloaded successfully.");
                    }
                    terminal.writer().flush();
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

    private static void openSettingsFileInEditor() {
        openFileWithFallback(settingsManager.getSettingsFilePath().toFile());
    }

    /**
     * Opens a file using configured editor or falls back to Desktop API.
     */
    private static void openFileWithFallback(java.io.File file) {
        String editor = settings != null ? settings.getSingleFileEditor() : null;

        // Try configured editor first
        if (editor != null && !editor.isEmpty()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(editor, file.getAbsolutePath());
                pb.start();
                terminal.writer().println("Opening file in configured editor...");
                terminal.writer().flush();
                return;
            } catch (Exception e) {
                terminal.writer().println("WARNING: Failed to open with configured editor: " + e.getMessage());
                terminal.writer().println("Falling back to system default editor...");
                terminal.writer().flush();
            }
        }

        // Fallback to java.awt.Desktop
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.EDIT)) {
                    desktop.edit(file);
                    terminal.writer().println("Opening file with system default editor...");
                    terminal.writer().flush();
                } else if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(file);
                    terminal.writer().println("Opening file with system default application...");
                    terminal.writer().flush();
                } else {
                    terminal.writer().println("ERROR: No way to open file - Desktop API not supported.");
                    terminal.writer().println("File location: " + file.getAbsolutePath());
                    terminal.writer().flush();
                }
            } else {
                terminal.writer().println("ERROR: Desktop API not supported on this system.");
                terminal.writer().println("File location: " + file.getAbsolutePath());
                terminal.writer().flush();
            }
        } catch (Exception e) {
            terminal.writer().println("ERROR: Failed to open file: " + e.getMessage());
            terminal.writer().println("File location: " + file.getAbsolutePath());
            terminal.writer().flush();
        }
    }

    private static void manageOrganizations() {
        while (true) {
            terminal.writer().println("\n--- Manage Organizations ---");
            terminal.writer().println("Current Organizations:");
            for (int i = 0; i < settings.getOrganizations().size(); i++) {
                Organization org = settings.getOrganizations().get(i);
                terminal.writer().println((i + 1) + ". " + org.getName() + " (groupId: " + org.getGroupId() + ")");
            }
            terminal.writer().println();
            terminal.writer().println("1. (A)dd Organization");
            terminal.writer().println("2. (E)dit Organization");
            terminal.writer().println("3. (R)emove Organization");
            terminal.writer().println("0. E(x)it (Esc)");
            terminal.writer().println();
            terminal.writer().flush();

            String choice = readSingleKey();

            switch (choice.toLowerCase()) {
                case "1":
                case "a":
                    String newOrgName = lineReader.readLine("Enter organization name: ").trim();
                    if (newOrgName.isEmpty()) {
                        terminal.writer().println("Organization name cannot be empty.");
                        terminal.writer().flush();
                        break;
                    }

                    boolean exists = false;
                    for (Organization org : settings.getOrganizations()) {
                        if (org.getName().equals(newOrgName)) {
                            exists = true;
                            break;
                        }
                    }

                    if (exists) {
                        terminal.writer().println("Organization already exists.");
                        terminal.writer().flush();
                        break;
                    }

                    String newGroupId = lineReader.readLine("Enter Maven groupId (e.g., io.github." + newOrgName + "): ").trim();
                    if (newGroupId.isEmpty()) {
                        newGroupId = "io.github." + newOrgName;
                    }

                    settings.getOrganizations().add(new Organization(newOrgName, newGroupId));
                    settingsManager.saveSettings(settings);
                    terminal.writer().println("Organization added.");
                    terminal.writer().flush();
                    break;

                case "2":
                case "e":
                    if (settings.getOrganizations().isEmpty()) {
                        terminal.writer().println("No organizations to edit.");
                        terminal.writer().flush();
                        break;
                    }

                    String editIndexStr = lineReader.readLine("Enter organization number to edit: ");
                    try {
                        int editIndex = Integer.parseInt(editIndexStr) - 1;
                        if (editIndex >= 0 && editIndex < settings.getOrganizations().size()) {
                            Organization orgToEdit = settings.getOrganizations().get(editIndex);

                            terminal.writer().println("Current name: " + orgToEdit.getName());
                            String newName = lineReader.readLine("Enter new name (or press Enter to keep): ").trim();
                            if (!newName.isEmpty()) {
                                orgToEdit.setName(newName);
                            }

                            terminal.writer().println("Current groupId: " + orgToEdit.getGroupId());
                            String newGid = lineReader.readLine("Enter new groupId (or press Enter to keep): ").trim();
                            if (!newGid.isEmpty()) {
                                orgToEdit.setGroupId(newGid);
                            }

                            settingsManager.saveSettings(settings);
                            terminal.writer().println("Organization updated.");
                        } else {
                            terminal.writer().println("Invalid number.");
                        }
                    } catch (NumberFormatException e) {
                        terminal.writer().println("Invalid input.");
                    }
                    terminal.writer().flush();
                    break;

                case "3":
                case "r":
                    if (settings.getOrganizations().isEmpty()) {
                        terminal.writer().println("No organizations to remove.");
                        terminal.writer().flush();
                        break;
                    }

                    String removeIndexStr = lineReader.readLine("Enter organization number to remove: ");
                    try {
                        int removeIndex = Integer.parseInt(removeIndexStr) - 1;
                        if (removeIndex >= 0 && removeIndex < settings.getOrganizations().size()) {
                            settings.getOrganizations().remove(removeIndex);
                            settingsManager.saveSettings(settings);
                            terminal.writer().println("Organization removed.");
                        } else {
                            terminal.writer().println("Invalid number.");
                        }
                    } catch (NumberFormatException e) {
                        terminal.writer().println("Invalid input.");
                    }
                    terminal.writer().flush();
                    break;

                case "0":
                case "x":
                case "\u001B": // ESC key
                    break; // Return to main menu

                default:
                    terminal.writer().println("Invalid option.");
                    terminal.writer().flush();
            }
        }
    }

    /**
     * Opens the current directory in the configured IDE.
     */
    private static void openCurrentDirInIDE() {
        String ideLauncher = settings.getIdeLauncher();

        if (ideLauncher == null || ideLauncher.isEmpty()) {
            terminal.writer().println("ERROR: IDE Launcher not configured in settings.");
            terminal.writer().println("Please configure it in Settings menu (option 5).");
            terminal.writer().flush();
            return;
        }

        try {
            String currentDir = System.getProperty("user.dir");
            terminal.writer().println("\n--- Open in IDE ---");
            terminal.writer().println("Current directory: " + currentDir);
            terminal.writer().println("Opening in IDE...");
            terminal.writer().flush();

            // Replace %PATH% placeholder with current directory
            String command = ideLauncher.replace("%PATH%", currentDir);

            // Parse and execute command
            String[] parts = parseCommand(command);
            if (parts.length == 0) {
                terminal.writer().println("ERROR: Invalid IDE launcher command.");
                terminal.writer().flush();
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(parts);
            pb.start();

            terminal.writer().println("✓ Opened current directory in IDE.");
            terminal.writer().flush();

        } catch (Exception e) {
            terminal.writer().println("ERROR: Failed to open IDE: " + e.getMessage());
            terminal.writer().flush();
        }
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

    /**
     * Returns cached projects or null if not yet scanned.
     * This ensures we only scan once per application run.
     */
    private static java.util.concurrent.atomic.AtomicReference<java.util.List<String>> getCachedProjects() {
        return new java.util.concurrent.atomic.AtomicReference<>(cachedProjects);
    }

    /**
     * Updates the cached projects after scan completes.
     */
    public static void updateCachedProjects(java.util.List<String> projects) {
        cachedProjects = projects;
    }

    // ============== CLI Subcommands ==============

    @Command(name = "open", aliases = {"o"}, description = "Open project picker to select and navigate to projects")
    static class OpenCommand implements Runnable {
        @picocli.CommandLine.Option(names = "--no-daemon", description = "Run the picker in this process instead of the daemon")
        boolean noDaemon;

        @Override
        public void run() {
            try {
                if (!noDaemon) {
                    // The daemon keeps JavaFX and ArcadeDB warm, which is nearly all of the
                    // startup cost. Null means it could not be reached; fall through rather than
                    // fail, because a picker that opens slowly beats one that does not open.
                    var exitCode = littlejlib.proj.daemon.OpenViaDaemon.open(isVerbose());
                    if (exitCode != null) {
                        System.exit(exitCode);
                    }
                }
                ProjectManager.openProject(terminal, lineReader, settings, getCachedProjects());
            } catch (Exception e) {
                System.err.println("Error opening project: " + e.getMessage());
                e.printStackTrace();
                System.exit(3);
            }
        }
    }

    @Command(name = "new", aliases = {"n"}, description = "Create a new project")
    static class NewCommand implements Runnable {
        @Override
        public void run() {
            try {
                ProjectManager.createNewProject(terminal, lineReader, settings);
            } catch (Exception e) {
                System.err.println("Error creating new project: " + e.getMessage());
                e.printStackTrace();
                System.exit(3);
            }
        }
    }

    // 'clone' is now cli.CloneCommands.CloneCmd, which takes the repository as an argument instead
    // of prompting for it. GitManager's prompting version is still what the main menu uses.

    /**
     * With no subcommand this is the interactive window it has always been; with one it is
     * scriptable. Keeping both on the same verb means muscle memory still works and an agent has a
     * way in that does not require a display.
     */
    @Command(name = "prp", aliases = {"p"}, description = "Manage Project Requirement Prompts (PRPs)",
             subcommands = {
                 littlejlib.proj.cli.PrpCommands.NewCmd.class,
                 littlejlib.proj.cli.PrpCommands.ListCmd.class,
                 littlejlib.proj.cli.PrpCommands.ShowCmd.class,
                 littlejlib.proj.cli.PrpCommands.AgentCmd.class,
                 littlejlib.proj.cli.PrpCommands.DirCmd.class
             })
    static class PrpCommand implements Runnable {
        @Override
        public void run() {
            try {
                AITemplateManager.manageTemplatesUI(settings);
            } catch (Exception e) {
                System.err.println("Error managing PRPs: " + e.getMessage());
                e.printStackTrace();
                System.exit(3);
            }
        }
    }

    /**
     * The read-only index commands. Each is a thin shell that hands its arguments to the query
     * layer, which decides whether to ask the daemon or open the database itself - the caller must
     * not have to know which, and getting it wrong means a lock error on a working machine.
     */
    abstract static class QueryBackedCommand implements Runnable {
        abstract String verb();

        abstract java.util.List<String> arguments();

        @Override
        public void run() {
            var args = new java.util.ArrayList<String>();
            args.add(verb());
            args.addAll(arguments());
            var code = littlejlib.proj.cli.IndexAccess.execute(args);
            if (code != 0) System.exit(code);
        }
    }

    @Command(name = "list", aliases = {"ls"}, description = "List indexed projects (--maven, --concern, --base, --paths, --json)")
    static class ListCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "list"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "find", aliases = {"f"}, description = "Search indexed projects by path or maven coordinates")
    static class FindCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "find"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "info", aliases = {"i"}, description = "Everything known about one project (defaults to the current directory)")
    static class InfoCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "info"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "deps", aliases = {"d"}, description = "Dependencies of a project (--local, --absent, --reverse, --json)")
    static class DepsCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "deps"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "central", description = "Maven Central publication status across the index (--sweep)")
    static class CentralCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "central"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "scan", description = "Re-walk every coderepobase and rebuild the index (--central, --no-files)")
    static class ScanCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "scan"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "files", aliases = {"fl"},
             description = "Find source files by name across every indexed project (--exact, --ext, --paths)")
    static class FilesCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "files"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "where", description = "Which indexed project provides these maven coordinates")
    static class WhereCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "where"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "impact", description = "Projects that break if this one changes, transitively")
    static class ImpactCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "impact"; }

        java.util.List<String> arguments() { return args; }
    }

    @Command(name = "blocked", description = "Projects blocked from publishing by an unpublished local dependency")
    static class BlockedCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "blocked"; }

        java.util.List<String> arguments() { return args; }
    }

    /**
     * A terminal, here or somewhere named, in whatever terminal the user actually uses. The picker
     * has had this per row for a while; there was no way to ask for it from a prompt.
     */
    @Command(name = "term", aliases = {"t"}, description = "Open your terminal in a directory (default: here)")
    static class TermCommand implements Runnable {
        @picocli.CommandLine.Parameters(arity = "0..1", description = "Directory; defaults to the current one")
        String dir;

        @picocli.CommandLine.Option(names = "--which", description = "Print which terminal would be used and exit")
        boolean which;

        @Override
        public void run() {
            var s = new SettingsManager().loadSettings();
            var terminal = littlejlib.proj.terminal.TerminalRunner.resolve(s);
            if (which) {
                var runner = littlejlib.proj.terminal.TerminalRunner.runner(s);
                littlejlib.proj.StdoutChannel.data().println("opens a shell:  " + terminal.getName());
                littlejlib.proj.StdoutChannel.data().println("   " + terminal.getOpenCommand());
                if (terminal.canRun()) {
                    littlejlib.proj.StdoutChannel.data().println("runs commands:  " + terminal.getName());
                    littlejlib.proj.StdoutChannel.data().println("   " + terminal.getRunCommand());
                } else {
                    // Saying "run: null" would be technically accurate and useless. What the user
                    // needs to know is which terminal their agent will actually start in.
                    littlejlib.proj.StdoutChannel.data().println(terminal.getName() + " cannot run a command from the "
                            + "command line, so agents start in:");
                    littlejlib.proj.StdoutChannel.data().println("runs commands:  "
                            + (runner == null ? "nothing available" : runner.getName()));
                    if (runner != null) littlejlib.proj.StdoutChannel.data().println("   " + runner.getRunCommand());
                }
                return;
            }
            var target = java.nio.file.Path.of(dir == null || dir.isBlank()
                    ? System.getProperty("user.dir") : dir).toAbsolutePath();
            if (!littlejlib.proj.terminal.TerminalRunner.open(target, s)) {
                LogConfig.err().println("could not open " + terminal.getName());
                System.exit(3);
            }
        }
    }

    @Command(name = "global-prp", aliases = {"gp"}, description = "Global PRP viewer - browse PRPs across all projects")
    static class GlobalPrpCommand implements Runnable {
        @Override
        public void run() {
            try {
                AITemplateManager.manageGlobalTemplatesUI(settings);
            } catch (Exception e) {
                System.err.println("Error opening Global PRP viewer: " + e.getMessage());
                e.printStackTrace();
                System.exit(3);
            }
        }
    }

    /**
     * The scriptable half of settings. Named separately from {@code settings} so that the verb which
     * opens a window and the verb which edits a key are never confused for one another - an agent
     * calling the wrong one would hang waiting for a window nobody can see.
     */
    @Command(name = "config", aliases = {"c"},
             description = "Read and write settings from the command line (list|get|set|unset|add|drop|path|edit|validate)",
             subcommands = {
                 littlejlib.proj.cli.ConfigCommands.ListCmd.class,
                 littlejlib.proj.cli.ConfigCommands.GetCmd.class,
                 littlejlib.proj.cli.ConfigCommands.SetCmd.class,
                 littlejlib.proj.cli.ConfigCommands.UnsetCmd.class,
                 littlejlib.proj.cli.ConfigCommands.AddCmd.class,
                 littlejlib.proj.cli.ConfigCommands.DropCmd.class,
                 littlejlib.proj.cli.ConfigCommands.PathCmd.class,
                 littlejlib.proj.cli.ConfigCommands.EditCmd.class,
                 littlejlib.proj.cli.ConfigCommands.ValidateCmd.class
             })
    static class ConfigCommand implements Runnable {
        @Override
        public void run() {
            // Bare 'proj config' lists, because that is what you want when you cannot remember a key.
            new picocli.CommandLine(new littlejlib.proj.cli.ConfigCommands.ListCmd())
                    .execute();
        }
    }

    @Command(name = "settings", aliases = {"s"}, description = "Settings window (--text for the old terminal menu)")
    static class SettingsCommand implements Runnable {
        @picocli.CommandLine.Option(names = "--text",
                description = "Use the terminal menu instead of the window")
        boolean text;

        @Override
        public void run() {
            try {
                if (text) {
                    settingsMenu();
                    return;
                }
                // The window is the default now. The terminal menu can edit a path and nothing
                // else worth editing - command templates are the settings that actually need
                // correcting, and typing one blind into a JLine prompt is how they end up wrong.
                littlejlib.proj.ui.JavaFXManager.getInstance().showDialog(
                        (lock, holder) -> javafx.application.Platform.runLater(() -> {
                            var saved = littlejlib.proj.ui.SettingsDialog
                                    .showOnFxThread(null, settings);
                            synchronized (lock) {
                                holder[0] = saved ? "SAVED" : "CANCELLED";
                                lock.notifyAll();
                            }
                        }),
                        3_600_000);
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Error accessing settings: " + e.getMessage());
                e.printStackTrace();
                System.exit(3);
            }
        }
    }

    @Command(name = "ide", aliases = {"e"}, description = "Open current directory as project in configured IDE")
    static class IdeCommand implements Runnable {
        @Override
        public void run() {
            try {
                openCurrentDirInIDE();
            } catch (Exception e) {
                System.err.println("Error opening IDE: " + e.getMessage());
                e.printStackTrace();
                System.exit(3);
            }
        }
    }

    @Command(name = "help", aliases = {"h", "-h", "--help"}, description = "Show this help message")
    static class HelpCommand implements Runnable {
        @picocli.CommandLine.Spec
        private picocli.CommandLine.Model.CommandSpec spec;

        @Override
        public void run() {
            // Print header first to ensure visibility
            System.out.println("========================================");
            System.out.println("  Project Build & Dev Tools");
            System.out.println("  Version 1.0");
            System.out.println("========================================");
            System.out.println();

            // Manually print simple help
            System.out.println("Usage: prj [COMMAND]");
            System.out.println();
            System.out.println("Commands:");
            System.out.println("Interactive:");
            System.out.println("  open, o       Open project picker to select and navigate to projects");
            System.out.println("  new, n        Create a new project");
            System.out.println("  clone, g      Clone into the right base   <url|org/repo> --base X --print");
            System.out.println("  prp, p        PRP manager window (see the scriptable subcommands below)");
            System.out.println("  global-prp, gp  Global PRP viewer - browse PRPs across all projects");
            System.out.println("  config, c     Read and write any setting from a terminal:");
            System.out.println("                  config list [--keys --json]   config get <key>");
            System.out.println("                  config set <key> <value...>   config unset <key>");
            System.out.println("                  config add|drop <listkey> <value...>");
            System.out.println("                  config path | edit | validate");
            System.out.println("  settings, s   Settings window - code bases, terminals, IDEs, agent   --text");
            System.out.println("  ide, e        Open current directory as project in configured IDE");
            System.out.println();
            System.out.println("Scriptable - read the index, no window, machine readable with --json:");
            System.out.println("  list, ls      List indexed projects   --maven --concern X --base X --paths --json");
            System.out.println("  find, f       Search by path or maven coordinates   <terms...> --paths --json");
            System.out.println("  info, i       Everything known about one project   [dir] --json");
            System.out.println("  deps, d       Dependencies of a project   [dir] --local --absent --reverse --json");
            System.out.println("  central       Maven Central status across the index   --sweep --json");
            System.out.println("  files, fl     Find source files by name   <terms> --exact X --ext java --paths");
            System.out.println("  where         Which project provides <groupId:artifactId>");
            System.out.println("  impact        What breaks if this project changes, transitively   [dir]");
            System.out.println("  blocked       Projects that cannot publish until a local dep is released");
            System.out.println("  scan          Re-walk every coderepobase and rebuild the index   --central --json");
            System.out.println();
            System.out.println("Scriptable - PRPs, no window:");
            System.out.println("  prp new <name>   Create the next numbered PRP   --dir --index --open --json");
            System.out.println("  prp list         List a project's PRPs   --dir --open-only --json");
            System.out.println("  prp show <n>     Every file belonging to one PRP   --dir --json");
            System.out.println("  prp agent <n>    Open a terminal at the PROJECT root running a coding");
            System.out.println("                   agent on that PRP   --dir --print   (alias: prp claude)");
            System.out.println("  prp new <name> --agent    create it and start the agent in one step");
            System.out.println("  prp dir          Print the prp folder these act on   --dir");
            System.out.println();
            System.out.println("  term, t       Open your terminal in a directory   [dir] --which");
            System.out.println();
            System.out.println("Housekeeping:");
            System.out.println("  daemon        Inspect or control the background daemon (status|start|stop|restart|log)");
            System.out.println("  repobase, rb  List, add or remove coderepobases (add needs --type primary|mirror|auxiliary)");
            System.out.println("  help, h       Show this help message");
            System.out.println();
            System.out.println("--concern accepts: all, maven, not-maven, not-on-central, deps-not-on-central,");
            System.out.println("                   has-local-deps, not-on-github, no-remote, no-git");
            System.out.println();
            System.out.println("Run 'prj' without arguments to use interactive menu mode.");
            System.out.println();
            System.out.flush();
        }
    }
}
