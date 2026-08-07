package littlejlib.proj;

/**
 * One named terminal, and how to ask it to do the two things this tool needs.
 * <p>
 * Two templates, not one, because opening a shell somewhere and running something there are
 * genuinely different invocations for every terminal worth having. Tabby spells them
 * {@code open <dir>} and {@code run <command>}; Windows Terminal takes {@code -d <dir>} for both;
 * cmd needs {@code start "" /D <dir>}.
 * <p>
 * Placeholders: {@code %PATH%} is the directory, {@code %SCRIPT%} is a generated script that changes
 * into that directory and then runs the command. The script exists so that {@code runCommand} does
 * not have to solve "start in this directory" separately for every terminal - Tabby's {@code run}
 * verb, for one, takes no directory at all.
 */
public class TerminalLauncher {

    private String name;
    private String openCommand;   // %PATH%
    private String runCommand;    // %SCRIPT%, and %PATH% where the terminal accepts one

    public TerminalLauncher() { }

    public TerminalLauncher(String name, String openCommand, String runCommand) {
        this.name = name;
        this.openCommand = openCommand;
        this.runCommand = runCommand;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOpenCommand() {
        return openCommand;
    }

    public void setOpenCommand(String openCommand) {
        this.openCommand = openCommand;
    }

    public String getRunCommand() {
        return runCommand;
    }

    public void setRunCommand(String runCommand) {
        this.runCommand = runCommand;
    }

    /** Openable is the minimum; a terminal with no run template still gets you a shell. */
    public boolean isUsable() {
        return name != null && !name.isBlank() && openCommand != null && !openCommand.isBlank();
    }

    public boolean canRun() {
        return runCommand != null && !runCommand.isBlank();
    }

    @Override
    public String toString() {
        return name + " -> " + openCommand;
    }
}
