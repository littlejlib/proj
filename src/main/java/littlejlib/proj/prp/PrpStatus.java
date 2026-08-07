package littlejlib.proj.prp;

public enum PrpStatus {
    ACTIVE("ACTIVE", "#1976D2", "#E3F2FD"),
    CLOSED("CLOSED", "#757575", "#F5F5F5");

    private final String label;
    private final String textColor;
    private final String backgroundColor;

    PrpStatus(String label, String textColor, String backgroundColor) {
        this.label = label;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
    }

    public String getLabel() {
        return label;
    }

    public String getTextColor() {
        return textColor;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    @Override
    public String toString() {
        return label;
    }
}
