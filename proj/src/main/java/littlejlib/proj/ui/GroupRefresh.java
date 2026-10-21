package littlejlib.proj.ui;

import javafx.application.Platform;
import littlejlib.proj.group.ProjectGroup;
import littlejlib.proj.prp.GlobalPrpEntry;
import littlejlib.proj.prp.GlobalPrpScanner;
import littlejlib.proj.workspace.WorkspaceRecord;
import littlejlib.proj.workspace.WorkspaceStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What a group tab loads, and when.
 */
public final class GroupRefresh {

    private GroupRefresh() { }

    public static void onShown(GroupTab tab, Supplier<List<Path>> knownProjects) {
        if (tab.prps) refreshPrps(tab, knownProjects);
        if (!tab.group.isCode() && tab.folders != null) loadFolders(tab);
    }

    public static void refreshPrps(GroupTab tab, Supplier<List<Path>> knownProjects) {
        // Scanned per opening of the pane rather than once ever: PRPs are edited constantly,
        tab.prpPanel.showGlobal(() -> prpsOf(tab.group, knownProjects),
                tab.group.isCode() ? "every project" : tab.group.displayLabel());
    }

    static void loadFolders(GroupTab tab) {
        var folders = tab.folders;
        if (folders == null) return;
        new Thread(() -> {
            var loaded = rowsOf(tab.group);
            // Indexed rows first and the walk afterwards, in that order and never the other way:
            // the base is a network filesystem measured in tens of seconds, so a tab that waited
            // for it would be an empty tab for half a minute every time it was opened.
            Platform.runLater(() -> {
                folders.setRows(loaded);
                folders.refreshIfDue();
            });
        }, "Workspace-Load").start();
    }

    public static List<WorkspaceRecord> rowsOf(ProjectGroup group) {
        try {
            return new WorkspaceStore().loadGroup(group.slug());
        } catch (Exception e) {
            System.err.println("[GroupRefresh] could not read '" + group.slug() + "': " + e);
            return List.of();
        }
    }

    public static List<GlobalPrpEntry> prpsOf(ProjectGroup group, Supplier<List<Path>> knownProjects) {
        return group.isCode() ? codePrps(group, knownProjects) : plainPrps(group);
    }

    static List<GlobalPrpEntry> codePrps(ProjectGroup group, Supplier<List<Path>> knownProjects) {
        var known = knownProjects == null ? List.<Path>of() : knownProjects.get();
        if (!known.isEmpty()) return GlobalPrpScanner.scanProjects(known);

        // Only when the index is empty or unbuilt - walking the code bases took 24 seconds.
        var all = new ArrayList<GlobalPrpEntry>();
        for (var base : group.existingBasePaths()) {
            all.addAll(GlobalPrpScanner.scanAllProjects(base, group.depth()));
        }
        return all;
    }

    static List<GlobalPrpEntry> plainPrps(ProjectGroup group) {
        var dirs = rowsOf(group).stream().filter(WorkspaceRecord::hasPrp)
                .map(WorkspaceRecord::dir).filter(Objects::nonNull).toList();
        return GlobalPrpScanner.scanProjects(dirs);
    }
}
