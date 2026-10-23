package littlejlib.proj;

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
             Main.CctCommand.class,
             Main.WsCommand.class,
             Main.ConfigCommand.class,
             littlejlib.proj.cli.ShellCommands.ShellCmd.class,
             littlejlib.proj.cli.AotCommands.AotCmd.class,
             littlejlib.proj.cli.HibCli.class,
             Main.HelpCommand.class
         },
         footer = "%nRun 'prj' without arguments to use interactive menu mode.%n")
public class Main implements Runnable {
    @picocli.CommandLine.Option(names = {"--verbose", "-v", "--debug"}, description = "Enable verbose/debug output")
    private boolean verboseOpt = false;

    private static Settings settings;
    private static SettingsManager settingsManager;
    // JLine is gone: see Prompt. It carried a WindowsStreamPump thread that drained the console
    // input queue, which corrupted every command proj handed back to its shell.

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
            if (!jarrunner.aot.AotState.creating()) return;
            var err = LogConfig.err();
            err.println("[proj] first run since the jar changed - warming up to build the startup");
            err.println("[proj] cache. This run is slower; the next ones are much faster.");
            littlejlib.proj.aot.AotWarmup.run(err);
        } catch (Throwable t) {
            // A warmup that fails must never take the user's command down with it.
        }
    }

    /// SetConsoleOutputCP(65001) in-process, via FFM, then System.out/err in UTF-8. This replaces the
    /// `chcp 65001` proj.bat used to run before the JVM. Only on Windows: elsewhere the terminal is UTF-8.
    static void enableUnicodeConsole() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) return;
        try {
            var kernel32 = java.lang.foreign.SymbolLookup.libraryLookup("kernel32", java.lang.foreign.Arena.global());
            var setOutputCp = java.lang.foreign.Linker.nativeLinker().downcallHandle(
                    kernel32.find("SetConsoleOutputCP").orElseThrow(),
                    java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_BOOLEAN, java.lang.foreign.ValueLayout.JAVA_INT));
            if (!(boolean) setOutputCp.invoke(65001)) return;
            System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
            System.setErr(new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable t) {
            // No console attached, or no native access. Nothing to do and nothing worth saying.
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
        if (args.length > 0 && jarrunner.aot.AotTrainer.WARMUP_FLAG.equals(args[0])) {
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

        // Bare "proj" - the main menu - goes the same way, and for a second reason: the picker it
        // leads to needs ArcadeDB, whose directory lock the daemon holds, so a menu shown here can
        // offer an option that cannot work. What comes back is either "done", or the name of the
        // one kind of item the daemon cannot carry out - the ones that prompt on this terminal.
        String menuAction = null;
        if (args.length == 0) {
            var outcome = littlejlib.proj.daemon.MenuViaDaemon.run(isVerbose());
            if (outcome != null) {
                if (outcome.action() == null) System.exit(outcome.exitCode());
                menuAction = outcome.action();
            }
        }

        {
            settingsManager = new SettingsManager();
            settings = settingsManager.loadSettings();

            // Handle first run setup
            if (settings == null) {
                handleFirstRun();
                System.exit(0);
            }

            // The daemon showed the menu and the user picked something that prompts on a terminal.
            if (menuAction != null) {
                runMenuAction(menuAction);
                System.exit(0);
            }

            // Check if CLI arguments provided
            if (args.length == 0) {
                // No daemon to show it, so the menu runs here - the old path, still the fallback.
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
        System.out.println("FIRST RUN DETECTED");
        System.out.println();
        System.out.println("A template Settings.toml file has been created at:");
        System.out.println("  " + settingsManager.getSettingsFilePath());
        System.out.println();
        System.out.println("Please edit this file to configure:");
        System.out.println("  - Your code repositories path");
        System.out.println("  - Default editor");
        System.out.println("  - Organizations with their groupIds");
        System.out.println("  - Other preferences");
        System.out.println();
        System.out.println("After editing the file, run this program again.");
        System.out.flush();

        try {
            String openNow = Prompt.ask("Open settings file now? (y/n): ").trim();
            if (openNow.equalsIgnoreCase("y")) {
                openFileWithFallback(settingsManager.getSettingsFilePath().toFile());
            }
        } catch (Exception e) {
            // Ignore - just exit
        }
    }

    private static void printBanner() {
        System.out.println("========================================");
        System.out.println("  Project Build & Dev Tools");
        System.out.println("  Version 1.0");
        System.out.println("========================================");
        System.out.println();
        System.out.flush();
    }

    /**
     * The menu items the daemon hands back because they prompt on a terminal it does not have, or
     * because "here" means the directory this process was started in rather than that one.
     */
    private static void runMenuAction(String action) {
        switch (action) {
            case "GIT_CLONE" -> GitManager.cloneRepository(settings);
            case "NEW_PROJECT" -> ProjectManager.createNewProject(settings);
            case "IDE_EDITOR" -> openCurrentDirInIDE();
            default -> System.err.println("[proj] the daemon asked for an unknown action: " + action);
        }
    }

    private static void mainMenu() {
        // Show main menu - show once, sub-actions don't loop back
        MainMenuDialog.Action action = MainMenuDialog.show(settings);

        switch (action) {
            case OPEN_PROJECT:
                // A daemon may have started since this menu opened - between the two, the whole
                // point of this window is an option that then cannot open the database.
                var exitCode = littlejlib.proj.daemon.OpenViaDaemon.open(isVerbose());
                if (exitCode != null) System.exit(exitCode);
                ProjectManager.openProject(settings, getCachedProjects());
                break;
            case GIT_CLONE:
                GitManager.cloneRepository(settings);
                break;
            case NEW_PROJECT:
                ProjectManager.createNewProject(settings);
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
                System.out.println("\nGoodbye!");
                System.out.flush();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.exit(0);
        }
    }

    /** One keypress. Was JLine raw mode, in three identical copies; now one place. */
    private static String readSingleKey() {
        return Prompt.key();
    }

    private static void settingsMenu() {
        while (true) {
            System.out.println("\n--- Settings ---");
            System.out.println("Current Settings:");
            System.out.println("  Code Repos Path: " + settings.getCodeReposPath());
            String mirrorPath = settings.getGitHubMirrorPath();
            System.out.println("  GitHub Mirror Path: " + (mirrorPath == null || mirrorPath.isEmpty()
                    ? "(not set - clones go to Code Repos Path)" : mirrorPath));
            System.out.println("  Single File Editor: " + settings.getSingleFileEditor());
            System.out.println("  IDE Launcher: " + settings.getIdeLauncher());
            System.out.print("  Organizations: ");
            if (settings.getOrganizations().isEmpty()) {
                System.out.println("(none)");
            } else {
                for (Organization org : settings.getOrganizations()) {
                    System.out.print(org.getName() + " ");
                }
                System.out.println();
            }
            System.out.println();
            System.out.println("1. Configure (C)ode Repository Path");
            System.out.println("2. Configure (G)itHub Mirror Path");
            System.out.println("3. Configure Project Scanning (D)epth");
            System.out.println("4. Manage (O)rganizations");
            System.out.println("5. Configure Single File (E)ditor");
            System.out.println("6. Configure Project IDE (L)auncher");
            System.out.println("7. Edit Settings File (R)aw");
            System.out.println("8. Show Settings (F)ile Location");
            System.out.println("9. Re(l)oad Settings from File");
            System.out.println("0. E(x)it to Main Menu (Esc)");
            System.out.println();
            System.out.flush();

            String choice = readSingleKey();

            switch (choice.toLowerCase()) {
                case "1":
                case "c":
                    String newPath = Prompt.ask("Enter new Code Repos Path: ");
                    settings.setCodeReposPath(newPath);
                    settingsManager.saveSettings(settings);
                    System.out.println("Settings saved.");
                    System.out.flush();
                    break;
                case "2":
                case "g":
                    System.out.println("\nSeparate directory for arbitrary cloned GitHub repos.");
                    System.out.println("Leave empty to clone into the Code Repos Path instead.");
                    System.out.flush();
                    String newMirror = Prompt.ask("Enter GitHub Mirror Path: ").trim();
                    settings.setGitHubMirrorPath(newMirror);
                    settingsManager.saveSettings(settings);
                    System.out.println("Settings saved.");
                    System.out.flush();
                    break;
                case "3":
                case "d":
                    String depthStr = Prompt.ask("Enter project scanning depth (current: " + settings.getProjectScanningDepth() + "): ");
                    try {
                        int depth = Integer.parseInt(depthStr.trim());
                        settings.setProjectScanningDepth(depth);
                        settingsManager.saveSettings(settings);
                        System.out.println("Settings saved.");
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid number. Settings not changed.");
                    }
                    System.out.flush();
                    break;
                case "4":
                case "o":
                    manageOrganizations();
                    break;
                case "5":
                case "e":
                    String newEditor = Prompt.ask("Enter Single File Editor (full path): ");
                    settings.setSingleFileEditor(newEditor);
                    settingsManager.saveSettings(settings);
                    System.out.println("Settings saved.");
                    System.out.flush();
                    break;
                case "6":
                case "l":
                    System.out.println("\nEnter IDE Launcher command template.");
                    System.out.println("Use %PATH% as placeholder for project path.");
                    System.out.println("Example: C:\\Program Files\\NetBeans\\bin\\netbeans.exe --open \"%PATH%\"");
                    System.out.flush();
                    String newLauncher = Prompt.ask("IDE Launcher: ");
                    settings.setIdeLauncher(newLauncher);
                    settingsManager.saveSettings(settings);
                    System.out.println("Settings saved.");
                    System.out.flush();
                    break;
                case "7":
                case "r":
                    openSettingsFileInEditor();
                    break;
                case "8":
                case "f":
                    System.out.println("Settings file: " + settingsManager.getSettingsFilePath());
                    System.out.flush();
                    break;
                case "9":
                    settings = settingsManager.loadSettings();
                    if (settings == null) {
                        System.out.println("Error reloading settings. Using previous settings.");
                        settings = settingsManager.loadSettings();
                    } else {
                        System.out.println("Settings reloaded successfully.");
                    }
                    System.out.flush();
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
                System.out.println("Opening file in configured editor...");
                System.out.flush();
                return;
            } catch (Exception e) {
                System.out.println("WARNING: Failed to open with configured editor: " + e.getMessage());
                System.out.println("Falling back to system default editor...");
                System.out.flush();
            }
        }

        // Fallback to java.awt.Desktop
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.EDIT)) {
                    desktop.edit(file);
                    System.out.println("Opening file with system default editor...");
                    System.out.flush();
                } else if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(file);
                    System.out.println("Opening file with system default application...");
                    System.out.flush();
                } else {
                    System.out.println("ERROR: No way to open file - Desktop API not supported.");
                    System.out.println("File location: " + file.getAbsolutePath());
                    System.out.flush();
                }
            } else {
                System.out.println("ERROR: Desktop API not supported on this system.");
                System.out.println("File location: " + file.getAbsolutePath());
                System.out.flush();
            }
        } catch (Exception e) {
            System.out.println("ERROR: Failed to open file: " + e.getMessage());
            System.out.println("File location: " + file.getAbsolutePath());
            System.out.flush();
        }
    }

    private static void manageOrganizations() {
        while (true) {
            System.out.println("\n--- Manage Organizations ---");
            System.out.println("Current Organizations:");
            for (int i = 0; i < settings.getOrganizations().size(); i++) {
                Organization org = settings.getOrganizations().get(i);
                System.out.println((i + 1) + ". " + org.getName() + " (groupId: " + org.getGroupId() + ")");
            }
            System.out.println();
            System.out.println("1. (A)dd Organization");
            System.out.println("2. (E)dit Organization");
            System.out.println("3. (R)emove Organization");
            System.out.println("0. E(x)it (Esc)");
            System.out.println();
            System.out.flush();

            String choice = readSingleKey();

            switch (choice.toLowerCase()) {
                case "1":
                case "a":
                    String newOrgName = Prompt.ask("Enter organization name: ").trim();
                    if (newOrgName.isEmpty()) {
                        System.out.println("Organization name cannot be empty.");
                        System.out.flush();
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
                        System.out.println("Organization already exists.");
                        System.out.flush();
                        break;
                    }

                    String newGroupId = Prompt.ask("Enter Maven groupId (e.g., io.github." + newOrgName + "): ").trim();
                    if (newGroupId.isEmpty()) {
                        newGroupId = "io.github." + newOrgName;
                    }

                    settings.getOrganizations().add(new Organization(newOrgName, newGroupId));
                    settingsManager.saveSettings(settings);
                    System.out.println("Organization added.");
                    System.out.flush();
                    break;

                case "2":
                case "e":
                    if (settings.getOrganizations().isEmpty()) {
                        System.out.println("No organizations to edit.");
                        System.out.flush();
                        break;
                    }

                    String editIndexStr = Prompt.ask("Enter organization number to edit: ");
                    try {
                        int editIndex = Integer.parseInt(editIndexStr) - 1;
                        if (editIndex >= 0 && editIndex < settings.getOrganizations().size()) {
                            Organization orgToEdit = settings.getOrganizations().get(editIndex);

                            System.out.println("Current name: " + orgToEdit.getName());
                            String newName = Prompt.ask("Enter new name (or press Enter to keep): ").trim();
                            if (!newName.isEmpty()) {
                                orgToEdit.setName(newName);
                            }

                            System.out.println("Current groupId: " + orgToEdit.getGroupId());
                            String newGid = Prompt.ask("Enter new groupId (or press Enter to keep): ").trim();
                            if (!newGid.isEmpty()) {
                                orgToEdit.setGroupId(newGid);
                            }

                            settingsManager.saveSettings(settings);
                            System.out.println("Organization updated.");
                        } else {
                            System.out.println("Invalid number.");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input.");
                    }
                    System.out.flush();
                    break;

                case "3":
                case "r":
                    if (settings.getOrganizations().isEmpty()) {
                        System.out.println("No organizations to remove.");
                        System.out.flush();
                        break;
                    }

                    String removeIndexStr = Prompt.ask("Enter organization number to remove: ");
                    try {
                        int removeIndex = Integer.parseInt(removeIndexStr) - 1;
                        if (removeIndex >= 0 && removeIndex < settings.getOrganizations().size()) {
                            settings.getOrganizations().remove(removeIndex);
                            settingsManager.saveSettings(settings);
                            System.out.println("Organization removed.");
                        } else {
                            System.out.println("Invalid number.");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input.");
                    }
                    System.out.flush();
                    break;

                case "0":
                case "x":
                case "\u001B": // ESC key
                    break; // Return to main menu

                default:
                    System.out.println("Invalid option.");
                    System.out.flush();
            }
        }
    }

    /**
     * Opens the current directory in the configured IDE.
     */
    private static void openCurrentDirInIDE() {
        String ideLauncher = settings.getIdeLauncher();

        if (ideLauncher == null || ideLauncher.isEmpty()) {
            System.out.println("ERROR: IDE Launcher not configured in settings.");
            System.out.println("Please configure it in Settings menu (option 5).");
            System.out.flush();
            return;
        }

        try {
            String currentDir = System.getProperty("user.dir");
            System.out.println("\n--- Open in IDE ---");
            System.out.println("Current directory: " + currentDir);
            System.out.println("Opening in IDE...");
            System.out.flush();

            // Replace %PATH% placeholder with current directory
            String command = ideLauncher.replace("%PATH%", currentDir);

            // Parse and execute command
            String[] parts = parseCommand(command);
            if (parts.length == 0) {
                System.out.println("ERROR: Invalid IDE launcher command.");
                System.out.flush();
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(parts);
            pb.start();

            System.out.println("✓ Opened current directory in IDE.");
            System.out.flush();

        } catch (Exception e) {
            System.out.println("ERROR: Failed to open IDE: " + e.getMessage());
            System.out.flush();
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
                ProjectManager.openProject(settings, getCachedProjects());
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
                ProjectManager.createNewProject(settings);
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
                 littlejlib.proj.cli.PrpResumeCmd.class,
                 littlejlib.proj.cli.PrpKeyCmd.class,
                 littlejlib.proj.cli.PrpCommands.PromptCmd.class,
                 littlejlib.proj.cli.PrpCommands.LogsCmd.class,
                 littlejlib.proj.cli.PrpCommands.InstructionsCmd.class,
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
     * The non-code groups: {@code proj ws list|scan}. Query-backed like every other index question,
     * so it reaches the daemon when one is running and opens the database itself when none is.
     */
    @Command(name = "ws", aliases = {"workspace"},
             description = "Folders that carry PRPs, in the groups configured as 'plain' (list, scan)")
    static class WsCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "ws"; }

        java.util.List<String> arguments() { return args; }
    }

    /**
     * The claude-code transcripts: {@code proj cct find|show|resume|export|index}. Query-backed for
     * the same reason every other index question is - the daemon holds the lock, so a command asks
     * it rather than fighting it. {@code proj cct} with nothing after it opens the window instead.
     */
    @Command(name = "cct", aliases = {"transcripts"},
             description = "Find the claude-code conversation you half-remember (find, show, resume, export, index)")
    static class CctCommand extends QueryBackedCommand {
        @picocli.CommandLine.Unmatched
        java.util.List<String> args = new java.util.ArrayList<>();

        String verb() { return "cct"; }

        java.util.List<String> arguments() { return args; }

        @Override
        public void run() {
            if (!args.isEmpty()) { super.run(); return; }
            var code = littlejlib.proj.daemon.OpenViaDaemon.open(isVerbose(), "transcripts");
            if (code != null) System.exit(code);
            // No daemon. The window opens here instead, which also opens the index here - the
            // whole reason the daemon route is tried first.
            var paths = settings.getBrowsePaths().stream().map(java.nio.file.Paths::get)
                    .filter(java.nio.file.Files::isDirectory).toList();
            littlejlib.proj.ui.ProjectPickerDialog.show(paths, settings, "transcripts");
        }
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
            System.out.println("  prp resume [n]    Resume the conversation that PRP already had");
            System.out.println("                   --list --session <id> --fork --print --remember --dir");
            System.out.println("  prp key [name]   This project's short name, so PRP 38 reads");
            System.out.println("                   billing-38 - a ticket id   --dir");
            System.out.println("  prp dir          Print the prp folder these act on   --dir");
            System.out.println();
            System.out.println("  term, t       Open your terminal in a directory   [dir] --which");
            System.out.println();
            System.out.println("Claude-code conversations - free RAM without losing the task:");
            System.out.println("  hib              Typed as !proj hib inside one: export its log, record it,");
            System.out.println("                   close the tab   --anyway (no PRP) --dry-run");
            System.out.println("  hib ls           Running ones: idle, RAM, committed   --full --json");
            System.out.println("  hib tab <name>   Hibernate others   --force --anyway --dry-run");
            System.out.println("  hib parked       Hibernated ones   [filter] --was --all --json");
            System.out.println("  hib restore <n>  Reopen in a new tab with claude --resume   --print --again");
            System.out.println("  hib suggest      Idle ones worth hibernating; dry run unless --apply, which");
            System.out.println("                   asks in a window (F2)   --min-idle <minutes> --json");
            System.out.println("  hib ui           The picker's Conversations tab (F8 hibernate, F9 restore)");
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
