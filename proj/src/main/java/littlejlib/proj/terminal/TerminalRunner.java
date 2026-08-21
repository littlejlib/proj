package littlejlib.proj.terminal;

import littlejlib.proj.Settings;
import littlejlib.proj.TerminalLauncher;
import littlejlib.proj.util.AppPaths;
import littlejlib.proj.util.Cmd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Opens a terminal somewhere, or opens one and runs something in it.
 * <p>
 * WHICH TERMINAL: whatever settings name as the default, else the first terminal configured by hand,
 * else the best one detected, else the OS fallback. A configured entry always beats a detected one,
 * because the only reason to configure one is that the detection got it wrong.
 * <p>
 * HOW THE COMMAND GETS ITS DIRECTORY: a generated script that changes directory first and then runs
 * the command. That indirection is not decoration - Tabby's {@code run} verb accepts no directory at
 * all, and every terminal spells "start here" differently. One script means the directory problem is
 * solved once, in a shell, rather than once per terminal in a command template that has to be right
 * the first time on a machine we cannot test.
 */
public final class TerminalRunner {

    private TerminalRunner() { }

    static final String OS = System.getProperty("os.name", "").toLowerCase();
    static final boolean WINDOWS = OS.contains("win");

    /** The terminal that will actually be used, never null. */
    public static TerminalLauncher resolve(Settings settings) {
        if (settings != null) {
            var configured = settings.getTerminalLaunchers().stream()
                    .filter(TerminalLauncher::isUsable).toList();

            var preferred = settings.getDefaultTerminal();
            if (preferred != null && !preferred.isBlank()) {
                var byName = configured.stream()
                        .filter(t -> preferred.equalsIgnoreCase(t.getName())).findFirst();
                if (byName.isPresent()) return byName.get();

                var detectedByName = TerminalDetector.detect().stream()
                        .filter(t -> preferred.equalsIgnoreCase(t.getName())).findFirst();
                if (detectedByName.isPresent()) return detectedByName.get();
            }

            if (!configured.isEmpty()) return configured.get(0);
        }

        var detected = TerminalDetector.detect();
        return detected.isEmpty() ? TerminalDetector.osFallback() : detected.get(0);
    }

    /**
     * The terminal that will be used to RUN something, which is not always the one used to open a
     * shell. Tabby is the clearest case: it opens a tab in a directory perfectly well and cannot be
     * told to run a command, so a user whose preferred terminal is Tabby still needs the agent to
     * start somewhere. Preference first, then any other terminal that can run, then the OS fallback.
     * Null only if even the fallback has no run template, which cannot happen as shipped.
     */
    public static TerminalLauncher runner(Settings settings) {
        var preferred = resolve(settings);
        if (preferred.canRun()) return preferred;

        if (settings != null) {
            var configured = settings.getTerminalLaunchers().stream()
                    .filter(TerminalLauncher::isUsable).filter(TerminalLauncher::canRun).findFirst();
            if (configured.isPresent()) return configured.get();
        }
        var detected = TerminalDetector.detect().stream().filter(TerminalLauncher::canRun).findFirst();
        if (detected.isPresent()) return detected.get();

        var fallback = TerminalDetector.osFallback();
        return fallback.canRun() ? fallback : null;
    }

    /** Just a shell, in this directory. */
    public static boolean open(Path directory, Settings settings) {
        return openIn(resolve(settings), directory);
    }

    /** The same, in a terminal the caller has already chosen. */
    public static boolean openIn(TerminalLauncher terminal, Path directory) {
        if (terminal == null || !terminal.isUsable()) return false;
        return Cmd.run(Cmd.parse(terminal.getOpenCommand()
                .replace(Cmd.PATH_PLACEHOLDER, directory.toAbsolutePath().toString())), null);
    }

    /**
     * A shell in this directory, running this command.
     *
     * @param label a short name for the generated script, so a stray one is identifiable
     */
    public static boolean run(Path directory, String command, String label, Settings settings) {
        return runIn(null, directory, command, label, settings);
    }

    /**
     * The same, in a named terminal. A null {@code terminal}, or one that cannot run a command,
     * falls back to whichever one can - so a caller may pass the user's pick without first having
     * to decide whether that pick is capable of the job.
     */
    public static boolean runIn(TerminalLauncher terminal, Path directory, String command,
                                String label, Settings settings) {
        return runIn(terminal, directory, command, label, settings, null);
    }

    public static boolean runIn(TerminalLauncher terminal, Path directory, String command,
                                String label, Settings settings, String title) {
        if (terminal == null || !terminal.canRun()) terminal = runner(settings);
        if (terminal == null) {
            System.err.println("[TerminalRunner] no terminal here can run a command; "
                    + "set a run template in Settings");
            return false;
        }

        Path script;
        try {
            script = writeScript(directory, command, label, title);
        } catch (IOException e) {
            System.err.println("[TerminalRunner] could not write the launch script: " + e.getMessage());
            return false;
        }

        var filled = terminal.getRunCommand()
                .replace("%SCRIPT%", script.toAbsolutePath().toString())
                .replace(Cmd.PATH_PLACEHOLDER, directory.toAbsolutePath().toString());
        return Cmd.run(Cmd.parse(filled), null);
    }

    /**
     * The script is left on disk rather than deleted: the terminal starts asynchronously, so
     * deleting it here would race the shell that is about to read it. They are small, they live in
     * one directory, and old ones are swept on the way in.
     */
    public static Path writeScript(Path directory, String command, String label) throws IOException {
        return writeScript(directory, command, label, null);
    }

    /**
     * @param title what the tab should be called. Set BY THE SCRIPT rather than by the terminal,
     *              because that works in every terminal at once: {@code title} on Windows and an
     *              OSC-0 escape elsewhere are both just the shell telling the emulator its name,
     *              and none of the terminals here has a CLI verb or an MCP tool for renaming a tab.
     */
    public static Path writeScript(Path directory, String command, String label, String title)
            throws IOException {
        var dir = AppPaths.home().resolve("launch");
        Files.createDirectories(dir);
        sweepOld(dir);

        var safe = (label == null ? "run" : label).replaceAll("[^A-Za-z0-9._-]", "_");
        var file = dir.resolve(safe + "-" + System.nanoTime() + (WINDOWS ? ".bat" : ".sh"));
        var target = directory.toAbsolutePath().toString();

        if (WINDOWS) {
            Files.writeString(file, "@echo off\r\n"
                    + (blank(title) ? "" : "title " + oneLine(title) + "\r\n")
                    + "cd /d \"" + target + "\"\r\n"
                    + command + "\r\n");
        } else {
            Files.writeString(file, "#!/bin/sh\n"
                    + (blank(title) ? "" : "printf '\\033]0;" + oneLine(title) + "\\007'\n")
                    + "cd \"" + target + "\" || exit 1\n"
                    + command + "\n");
            file.toFile().setExecutable(true);
        }
        return file;
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** A title is one line, and carries nothing a shell would read as syntax. */
    static String oneLine(String title) {
        return title.replaceAll("[\\r\\n%&|<>^\"]", " ").trim();
    }

    static final long SCRIPT_TTL_MS = java.time.Duration.ofDays(1).toMillis();

    static void sweepOld(Path dir) {
        try (var files = Files.list(dir)) {
            var cutoff = System.currentTimeMillis() - SCRIPT_TTL_MS;
            for (var f : files.toList()) {
                try {
                    if (Files.getLastModifiedTime(f).toMillis() < cutoff) Files.deleteIfExists(f);
                } catch (Exception e) {
                    // a script we cannot stat or delete is not worth failing a launch over
                }
            }
        } catch (Exception e) {
            // the sweep is housekeeping, never a precondition
        }
    }

    /** For the settings window: what is installed, plus anything already configured. */
    public static List<TerminalLauncher> available() {
        return TerminalDetector.detect();
    }

    /**
     * Every terminal a person could be offered, best first: the configured ones, then anything
     * detected that the configuration has not already claimed by name.
     * <p>
     * Ones that cannot RUN a command are included rather than filtered out. They can still open a
     * shell where the work is, which - with the command on the clipboard - is a perfectly good way
     * to start, and hiding a terminal the user can see installed only invites the question of why.
     */
    public static List<TerminalLauncher> choices(Settings settings) {
        var out = new java.util.ArrayList<TerminalLauncher>();
        var claimed = new java.util.HashSet<String>();

        if (settings != null) {
            for (var t : settings.getTerminalLaunchers()) {
                if (!t.isUsable()) continue;
                out.add(t);
                claimed.add(name(t));
            }
        }
        for (var t : TerminalDetector.detect()) {
            if (claimed.contains(name(t))) continue;
            out.add(t);
            claimed.add(name(t));
        }
        if (out.isEmpty()) out.add(TerminalDetector.osFallback());

        // The default first, so the button under the cursor is the one usually wanted.
        var preferred = settings == null ? null : settings.getDefaultTerminal();
        if (preferred != null && !preferred.isBlank()) {
            out.sort(java.util.Comparator.comparingInt(
                    t -> preferred.equalsIgnoreCase(t.getName()) ? 0 : 1));
        }
        return out;
    }

    static String name(TerminalLauncher t) {
        return t.getName() == null ? "" : t.getName().toLowerCase();
    }
}
