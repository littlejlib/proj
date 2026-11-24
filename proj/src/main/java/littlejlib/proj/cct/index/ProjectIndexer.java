package littlejlib.proj.cct.index;

import module java.base;

import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static littlejlib.proj.cct.Num.*;

/** One ProjectDoc per folder under {@code ~/.claude/projects}, rebuilt whole on every pass. */
public final class ProjectIndexer {

    private ProjectIndexer() {}

    public static int run(Db db, Collection<SessionDoc> sessions) {
        var rows = new ArrayList<ProjectDoc>();
        for (var dir : StoreScanner.projectDirs()) {
            var stats = FolderStats.of(dir);
            var cwd = resolveCwd(sessions, stats.encoded());
            rows.add(new ProjectDoc()
                .encoded(stats.encoded())
                .cwd(cwd)
                .cwdKey(FolderStats.cwdKey(cwd))
                .sessions(stats.sessionFiles())
                .subagents(stats.subagentFiles())
                .jsonlBytes(stats.jsonlBytes())
                .toolResultBytes(stats.toolResultBytes())
                .memoryBytes(stats.memoryBytes())
                .memoryFiles(stats.memoryFiles())
                .lastActivity(stats.lastModified()));
        }
        db.projects().replaceAll(rows);
        return rows.size();
    }

    private static String resolveCwd(Collection<SessionDoc> sessions, String encoded) {
        var best = (String) null;
        var bestAt = -1L;
        for (var s : sessions) {
            if (!Objects.equals(encoded, s.projectEncoded())) continue;
            if (s.cwd() == null || s.cwd().isBlank()) continue;
            if (n(s.lastActivity()) >= bestAt) { bestAt = n(s.lastActivity()); best = s.cwd(); }
        }
        return best;
    }
}
