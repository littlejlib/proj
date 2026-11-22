package littlejlib.proj.ui;

import javafx.scene.control.Tab;
import littlejlib.proj.group.ProjectGroup;

/**
 * One tab, and what it shows.
 */
public final class GroupTab {

    public final ProjectGroup group;
    public final boolean prps;
    public final Tab tab;

    /** Set when {@link #prps}; the group's own PRP list, never another group's. */
    public final PrpPanel prpPanel;

    /** Set for a PLAIN group's rows tab. Null for the code group, whose table is the picker's own. */
    public final WorkspacePane folders;

    public GroupTab(ProjectGroup group, boolean prps, Tab tab, PrpPanel prpPanel, WorkspacePane folders) {
        this.group = group;
        this.prps = prps;
        this.tab = tab;
        this.prpPanel = prpPanel;
        this.folders = folders;
    }

    public boolean isCodeRows() {
        return group.isCode() && !prps;
    }

    /** Where a bare letter and the Up arrow belong on this tab. */
    public void focusFilter() {
        if (prps && prpPanel != null) prpPanel.focusSearch();
        else if (folders != null) folders.focusFilter();
    }
}
