package littlejlib.proj.migrate;

import module java.base;

import littlejlib.proj.hib.HibRecord;
import littlejlib.proj.hib.LiveSession;
import littlejlib.proj.prp.PrpSessions;

/** PRP 56 decision 10: session rows, hibernated conversations and open tabs follow a folder's renumber map. */
public final class SessionMigration {

    static final int SHOW = 12;

    final boolean apply;
    final List<HibRecord> hib;
    final List<TabInfo> tabs;
    final List<LiveSession> live;
    final PrintStream out;

    public int folders, rows, rowsDone, hibs, hibsDone, renamed, toRename, runningCount;

    /** @param tabs null when fxterm-tabs is not running */
    public SessionMigration(boolean apply, List<HibRecord> hib, List<TabInfo> tabs, List<LiveSession> live, PrintStream out) {
        this.apply = apply;
        this.hib = hib;
        this.tabs = tabs;
        this.live = live;
        this.out = out;
    }

    public void folder(FolderMap map) {
        folders++;
        out.println(map.dir() + "  (" + map.mains().size() + " PRPs, short numbers " + map.width() + " wide)");
        var rw = new Rewriter(map);
        var found = PrpSessions.inFolder(map.dir());
        var rowMoves = found == null ? List.<Move>of()
                : found.stream().map(rw::forRow).filter(m -> m != null && m.changed()).toList();
        if (found == null) out.println("  session rows: NOT READ (the daemon does not know the query, or the database is unavailable); nothing said about them");
        else out.println("  session rows: " + found.size() + " in this folder, " + rowMoves.size() + " to change"
                + (apply ? "" : " (dry run)"));
        list(rowMoves);
        var hibMoves = HibRows.plan(rw, hib);
        out.println("  hibernated:   " + hibMoves.size() + " to change");
        list(hibMoves);
        rows += rowMoves.size();
        hibs += hibMoves.size();
        if (apply) applyData(rowMoves, hibMoves);
        var all = new ArrayList<>(rowMoves);
        all.addAll(hibMoves);
        reportTabs(map, all);
        reportRunning(map, all);
    }

    void applyData(List<Move> rowMoves, List<Move> hibMoves) {
        for (var m : rowMoves)
            if (PrpSessions.repoint(m.id(), Path.of(m.toFile()), m.toIndex(), m.toName())) rowsDone++;
            else out.println("  NOT CHANGED: row " + m.id() + " (no such row, or the daemon is older than this build)");
        try {
            hibsDone += HibRows.apply(hibMoves, hib);
        } catch (IOException e) {
            out.println("  NOT CHANGED: hibernated records: " + e.getMessage());
        }
    }

    void reportTabs(FolderMap map, List<Move> moves) {
        if (tabs == null) {
            out.println("  tabs:         fxterm-tabs is not running, so no tab was looked at");
            return;
        }
        var renames = TabMatch.renames(map, tabs, moves);
        for (var t : tabs) if (renames.containsKey(t.id())) {
            var to = renames.get(t.id());
            var done = apply && TabsCtl.rename(t.id(), to);
            if (done) renamed++;
            else toRename++;
            out.println("  tab " + t.id() + (t.live() ? " (live)" : " (asleep)") + ": " + TabMatch.clean(t.title()) + " -> " + to
                    + (done ? "  renamed" : apply ? "  NOT RENAMED" : "  would rename"));
        }
        var unmatched = TabMatch.unmatched(map, tabs, renames);
        unmatched.forEach(t -> out.println("  tab " + t.id() + " has an old number in its name and no record: " + TabMatch.clean(t.title())
                + "  (not renamed)"));
        TabMatch.byOtherTitle(map, tabs, moves, renames).forEach(t -> out.println("  tab " + t.id() + " runs a moved session under another title: "
                + TabMatch.clean(t.title()) + "  (not renamed)"));
    }

    void reportRunning(FolderMap map, List<Move> moves) {
        var sessions = TabMatch.running(map, live);
        runningCount += sessions.size();
        out.println("  running claude: " + sessions.size() + (sessions.isEmpty() ? "" : "  (keep their old name until resumed; nothing is closed)"));
        var ids = moves.stream().map(Move::id).collect(Collectors.toSet());
        for (var s : sessions)
            out.println("    pid " + s.pid() + "  " + s.label() + "  " + s.sessionId() + (ids.contains(s.sessionId()) ? "  (recorded on a moved PRP)" : "")
                    + "  " + s.cwd());
    }

    void list(List<Move> moves) {
        moves.stream().limit(SHOW).forEach(m -> out.println("    " + show(m.fromName()) + " -> " + show(m.toName()) + "   PRP " + show(m.fromIndex())
                + " -> " + m.toIndex() + "  (" + m.how() + ")"));
        if (moves.size() > SHOW) out.println("    ... and " + (moves.size() - SHOW) + " more");
    }

    static String show(String s) {
        return s == null ? "-" : s;
    }

    public void summary() {
        out.println();
        out.println(folders + " folder(s): session rows " + rows + (apply ? " (" + rowsDone + " changed)" : " to change")
                + ", hibernated " + hibs + (apply ? " (" + hibsDone + " changed)" : " to change")
                + ", tabs " + (apply ? renamed + " renamed, " + toRename + " not" : toRename + " would be renamed")
                + ", running claude sessions in these folders " + runningCount);
        if (!apply) out.println("dry run: nothing changed; add --apply to do it");
    }
}
