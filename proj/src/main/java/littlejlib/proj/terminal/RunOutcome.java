package littlejlib.proj.terminal;

/** What actually happened when a terminal was asked to run something. */
public enum RunOutcome {

    RAN,
    /**
     * Typed into the terminal that asked for it, and it runs when this process exits - so a caller
     * that gets this must finish and get out of the way rather than doing more work.
     */
    QUEUED,
    /** The shell is open in the right place, but the command was not delivered to it. */
    OPENED_ONLY,
    FAILED;

    public boolean opened() {
        return this != FAILED;
    }

    public String note() {
        return switch (this) {
            case RAN -> "started";
            case QUEUED -> "starting in this terminal";
            case OPENED_ONLY -> "the tab is open at the project root and the command is on the "
                    + "clipboard - paste it. Tabby's MCP plugin did not answer, and Tabby's own "
                    + "command line cannot run a command.";
            case FAILED -> "the terminal did not open";
        };
    }
}
