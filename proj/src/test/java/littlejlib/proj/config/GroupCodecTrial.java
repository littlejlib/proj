package littlejlib.proj.config;

import littlejlib.proj.group.Groups;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GroupCodecTrial {
    public static void main(String[] args) throws Exception {
        var home = Path.of(System.getProperty("user.home"), "littlejlib", "proj", "Settings.toml");
        var original = Files.readString(home);

        var s = SettingsCodec.read(original);
        System.out.println("code bases read: " + s.getCodeRepoBases().size()
                + ", groups read: " + s.getProjectGroups().size());

        SettingsCodec.set(s, "group.docs.label", List.of("Docs"));
        SettingsCodec.set(s, "group.docs.kind", List.of("plain"));
        SettingsCodec.set(s, "group.docs.depth", List.of("3"));
        SettingsCodec.set(s, "group.docs.paths", List.of("D:/shared/docs"));

        var written = SettingsCodec.write(s);
        var back = SettingsCodec.read(written);

        System.out.println("round trip groups: " + back.getProjectGroups());
        System.out.println("tabs: " + Groups.of(back).stream().map(g ->
                g.displayLabel() + "/" + g.groupKind().slug() + "/d" + g.depth()
                        + "/" + g.paths().size()).toList());

        var before = original.lines().filter(l -> l.startsWith("codebase.")).toList();
        var after = written.lines().filter(l -> l.startsWith("codebase.")).toList();
        System.out.println("codebase lines unchanged: " + before.equals(after));
        if (!before.equals(after)) { System.out.println("  was: " + before); System.out.println("  now: " + after); }

        var stable = SettingsCodec.write(back).equals(written);
        System.out.println("write is stable on re-write: " + stable);
        System.out.println("NOTHING WAS SAVED - this only round-trips in memory");
    }
}
