package littlejlib.proj.cct.store;

public enum Layer {

    HUMAN(1, "human"),
    ASSISTANT(2, "assistant"),
    THINKING(3, "thinking"),
    TOOLS(4, "tools");

    public final int depth;
    public final String label;

    Layer(int depth, String label) { this.depth = depth; this.label = label; }

    public boolean within(Layer widest) { return depth <= widest.depth; }

    public static Layer ofDepth(int d) {
        for (var l : values()) if (l.depth == d) return l;
        return HUMAN;
    }

    public static Layer ofLabel(String s) {
        if (s == null) return HUMAN;
        for (var l : values()) if (l.label.equalsIgnoreCase(s)) return l;
        return HUMAN;
    }
}
