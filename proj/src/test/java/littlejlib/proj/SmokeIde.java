package littlejlib.proj;

import littlejlib.proj.ide.ExeIcon;
import littlejlib.proj.ide.IdeDetector;

/** Lists what was actually found on this machine, and whether an icon could be pulled from each. */
public class SmokeIde {
    public static void main(String[] args) {
        var found = IdeDetector.detect();
        System.out.println("detected " + found.size() + " tools:");
        for (var ide : found) {
            var icon = ExeIcon.load(ide.exe, 16);
            System.out.println("  " + pad(ide.name, 12) + " folders=" + ide.opensFolders
                    + "  icon=" + (icon == null ? "NONE" : ((int) icon.getWidth() + "x" + (int) icon.getHeight()))
                    + "  " + ide.exe);
        }
        System.exit(0);
    }
    static String pad(String s, int n) { return s.length() >= n ? s : s + " ".repeat(n - s.length()); }
}
