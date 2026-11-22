package littlejlib.proj.migrate;

import module java.base;

import littlejlib.proj.hib.LiveSession;

/** Which open tabs and running conversations belong to a migrated folder. Pure: it reads lists and decides. */
final class TabMatch {

    private TabMatch() { }

    /** A tab title without the status mark claude-code puts in front of it. */
    static String clean(String title) {
        return title == null ? "" : title.replaceFirst("^[^A-Za-z0-9]+", "");
    }

    static boolean inProject(FolderMap map, String folder) {
        try {
            return folder != null && Path.of(folder).toAbsolutePath().normalize().startsWith(map.project());
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** Tabs of the folder whose title is exactly an old name of a planned move: tab id to its new title. Order kept. */
    static Map<String, String> renames(FolderMap map, List<TabInfo> tabs, List<Move> moves) {
        var out = new LinkedHashMap<String, String>();
        for (var t : tabs) {
            if (!inProject(map, t.folder())) continue;
            var name = clean(t.title());
            moves.stream().filter(m -> m.fromName() != null && m.fromName().equals(name) && !m.fromName().equals(m.toName()))
                    .findFirst().ifPresent(m -> out.put(t.id(), m.toName()));
        }
        return out;
    }

    /** Tabs of the folder with an old number in the title that no move accounts for: reported, never renamed. */
    static List<TabInfo> unmatched(FolderMap map, List<TabInfo> tabs, Map<String, String> renames) {
        return tabs.stream().filter(t -> inProject(map, t.folder()) && !renames.containsKey(t.id()) && map.looksOld(clean(t.title()))).toList();
    }

    /** Live tabs running one of the moved sessions under a title that is not its old name (for example claude's own): reported. */
    static List<TabInfo> byOtherTitle(FolderMap map, List<TabInfo> tabs, List<Move> moves, Map<String, String> renames) {
        var ids = moves.stream().map(Move::id).collect(Collectors.toSet());
        return tabs.stream().filter(t -> t.session() != null && ids.contains(t.session()) && !renames.containsKey(t.id())).toList();
    }

    /** Running claude conversations started in the project folder or below it. */
    static List<LiveSession> running(FolderMap map, List<LiveSession> live) {
        return live.stream().filter(s -> inProject(map, s.cwd())).toList();
    }
}
