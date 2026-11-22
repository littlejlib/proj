package littlejlib.proj.ui;

import littlejlib.proj.prp.GlobalPrpEntry;
import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpNumber;
import littlejlib.proj.prp.PrpSessions;
import littlejlib.proj.prp.TabName;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code billing-38} for a PRP, with the project's key looked up once per project.
 * <p>
 * The global list shows PRPs from many projects at once, where a bare {@code 38} identifies nothing;
 * the ticket does, and it is the same string the terminal tab and the log file carry.
 */
public final class PrpTickets {

    final Map<String, String> keys = new HashMap<>();
    final Map<String, Integer> widths = new HashMap<>();

    /** After a key changes, so a refresh shows the new one rather than the cached one. */
    public void clear() {
        keys.clear();
        widths.clear();
    }

    public String of(GlobalPrpEntry prp) {
        var index = prp.getIndex();
        if (index != null && !PrpNumber.isLegacy(index) && prp.getProjectPath() != null) {
            var w = widths.computeIfAbsent(prp.getProjectPath().toString(), p -> widthOf(prp.getProjectPath()));
            index = PrpNumber.shortForm(index, w);
        }
        return TabName.ticket(keyOf(prp.getProjectPath()), index);
    }

    static int widthOf(Path project) {
        try {
            return PrpNumber.width(PrpCreator.highestIndex(PrpCreator.prpDirOf(project)));
        } catch (IOException | RuntimeException e) {
            return PrpNumber.WIDTH;
        }
    }

    String keyOf(Path project) {
        if (project == null) return "prp";
        return keys.computeIfAbsent(project.toString(), p -> {
            var stored = PrpSessions.key(project);
            return stored != null ? stored : TabName.shortenProject(project);
        });
    }
}
