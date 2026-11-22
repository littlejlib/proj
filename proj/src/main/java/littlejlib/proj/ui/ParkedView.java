package littlejlib.proj.ui;

import littlejlib.proj.hib.HibRecord;

/** Which hibernated records the tab shows. Restored ones are rarely wanted, so they are a view of their own. */
public enum ParkedView {

    HIBERNATED("hibernated"), WAS("was hibernated"), ALL("all");

    public final String label;

    ParkedView(String label) {
        this.label = label;
    }

    public boolean shows(HibRecord r) {
        return switch (this) {
            case HIBERNATED -> r.hibernated();
            case WAS -> !r.hibernated();
            case ALL -> true;
        };
    }

    public ParkedView next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
