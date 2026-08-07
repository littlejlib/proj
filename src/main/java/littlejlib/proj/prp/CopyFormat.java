package littlejlib.proj.prp;

public enum CopyFormat {
    FULL_PATH("Full Path"),
    RELATIVE_PATH("Relative Path"),
    CLAUDE_PATH("Claude Code Path (@prp/...)"),
    FILE_NAME("File Name Only");

    private final String label;

    CopyFormat(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
