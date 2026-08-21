package littlejlib.proj.cct.index;

import module java.base;

import com.arcadedb.database.Database;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.*;

import static xyz.jphil.arcadedb.datahelper.Query.query;
import static littlejlib.proj.cct.Num.*;
import static littlejlib.proj.cct.model.ProjectDoc_A.$encoded;
import static littlejlib.proj.cct.model.SessionDoc_A.*;

public final class ProjectIndexer {

    private ProjectIndexer() {}

    public static int run(Database db) {
        var n = 0;
        for (var dir : StoreScanner.projectDirs()) {
            var stats = FolderStats.of(dir);
            var cwd = resolveCwd(db, stats.encoded());
            var doc = new ProjectDoc()
                .encoded(stats.encoded())
                .cwd(cwd)
                .cwdKey(FolderStats.cwdKey(cwd))
                .sessions(stats.sessionFiles())
                .subagents(stats.subagentFiles())
                .jsonlBytes(stats.jsonlBytes())
                .toolResultBytes(stats.toolResultBytes())
                .memoryBytes(stats.memoryBytes())
                .memoryFiles(stats.memoryFiles())
                .lastActivity(stats.lastModified());
            db.transaction(() -> doc.in(db).whereEq($encoded, doc.encoded()).upsert());
            n++;
        }
        return n;
    }

    private static String resolveCwd(Database db, String encoded) {
        var best = (String) null;
        var bestAt = -1L;
        for (var s : query(db, SessionDoc.TYPEDEF).eq($projectEncoded, encoded)) {
            if (s.cwd() == null || s.cwd().isBlank()) continue;
            if (n(s.lastActivity()) >= bestAt) { bestAt = n(s.lastActivity()); best = s.cwd(); }
        }
        return best;
    }
}
