package littlejlib.proj.cct.cli;

import module java.base;

import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.engine.ComponentFile;
import com.arcadedb.integration.backup.Backup;
import com.arcadedb.integration.restore.Restore;
import picocli.CommandLine.*;
import littlejlib.proj.cct.db.Db;

/**
 * Consistent backup of the transcript index, through ArcadeDB's own backup rather than a file copy:
 * a copy taken while the engine is running captures the buckets, the mtree and the WAL at different
 * instants and can fail to open.
 *
 * <p>Why this index is worth backing up at all, when it looks derived: {@code TurnDoc} stores the
 * message {@code text}, and indexing treats Claude's replies and thinking blocks as opt-OUT
 * ({@code --no-assistant}, {@code --no-thinking}). claude-code prunes transcript .jsonl files off
 * disk over time, so for every session pruned since it was indexed, this index holds the only
 * surviving copy. It is a derived index whose source is being deleted underneath it.
 *
 * <p>It lives here rather than in a script beside the tool because the database is held by whichever
 * proj process owns the server - the daemon or the picker - and an outside process simply cannot
 * open it. Running as a verb means it uses the same handle everything else does.
 *
 * <p>proj_cache is deliberately NOT backed up. That one really is derived, and {@code proj scan}
 * rebuilds it.
 */
@Command(name = "backup", description = "Back up the transcript index, verified by restoring it.")
public final class BackupCmd implements Callable<Integer> {

    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    static final String PREFIX = "cctranscripts-";

    /**
     * Defaults under the app home rather than to any particular shared location, so nothing here
     * assumes one machine's drive layout. That default is on the SAME DISK as the database, which is
     * not really a backup, so a run that uses it says so and tells you to pass --dest.
     */
    @Option(names = "--dest", description = "Where archives go. Defaults under the app home, which is"
        + " the same disk as the database - pass a path on other storage for a real backup.")
    String dest;

    @Option(names = "--keep", description = "How many archives to keep. Default 7.")
    int keep = 7;

    @Option(names = "--label", description = "Suffix for this archive, for a deliberate one-off.")
    String label;

    @Option(names = "--list", description = "List existing archives and take none.")
    boolean list;

    @Option(names = "--no-verify", description = "Skip the restore check. The archive is then a belief.")
    boolean noVerify;

    @Override
    public Integer call() throws Exception {
        var sameDisk = dest == null;
        var destDir = sameDisk
            ? Path.of(System.getProperty("user.home"), "littlejlib", "proj", "backups")
            : Path.of(dest);
        if (list) { listArchives(destDir); return 0; }
        if (sameDisk) Out.note("note: writing to " + destDir + ", which is the same disk as the index."
            + " A copy that dies with the disk is not a backup - pass --dest to put it elsewhere.");

        var name = PREFIX + STAMP.format(ZonedDateTime.now())
            + (label == null || label.isBlank() ? "" : "-" + label) + ".zip";
        var staging = Files.createTempDirectory("cctranscripts-backup-");
        try {
            var db = Db.get().db();
            var before = counts(db);
            before.forEach((t, n) -> Out.line("  %-14s %,10d", t, n));

            var produced = new Backup(db).setDirectory(staging.toString()).backupDatabase();
            var zip = resolve(staging, produced);
            Out.line("archive       : %s (%,.1f MB)", zip.getFileName(), Files.size(zip) / 1048576.0);

            if (noVerify) Out.line("verify        : SKIPPED - an unverified archive is a belief, not a backup");
            else verify(zip, before);

            Files.createDirectories(destDir);
            var published = destDir.resolve(name);
            Files.move(zip, published, StandardCopyOption.REPLACE_EXISTING);
            Out.line("published     : " + published);

            for (var old : prune(destDir, keep)) {
                Files.delete(old);
                Out.line("retention     : deleted " + old.getFileName());
            }
            return 0;
        } finally { deleteTree(staging); }
    }

    static Map<String, Long> counts(com.arcadedb.database.Database db) {
        var m = new TreeMap<String, Long>();
        for (var t : db.getSchema().getTypes()) {
            try (var rs = db.query("sql", "SELECT count(*) AS n FROM `" + t.getName() + "`")) {
                m.put(t.getName(), rs.hasNext() ? ((Number) rs.next().<Object>getProperty("n")).longValue() : 0L);
            }
        }
        return m;
    }

    static void verify(Path zip, Map<String, Long> expected) throws Exception {
        var tmp = Files.createTempDirectory("cctranscripts-verify-");
        try {
            var target = tmp.resolve(Db.NAME);
            new Restore(zip.toAbsolutePath().toString(), target.toString()).setVerboseLevel(0).restoreDatabase();
            Map<String, Long> actual;
            try (var f = new DatabaseFactory(target.toString())) {
                var d = f.open(ComponentFile.MODE.READ_ONLY);
                try { actual = counts(d); } finally { d.close(); }
            }
            var bad = new ArrayList<String>();
            Out.line("verify        : restored the archive and compared every type");
            for (var e : expected.entrySet()) {
                var got = actual.getOrDefault(e.getKey(), 0L);
                if (!got.equals(e.getValue())) bad.add(e.getKey() + " source " + e.getValue() + " restored " + got);
                Out.line("  %-14s %,10d  %s", e.getKey(), got, got.equals(e.getValue()) ? "ok" : "MISMATCH");
            }
            if (!bad.isEmpty()) throw new IllegalStateException("restore verification FAILED: " + String.join("; ", bad));
        } finally { deleteTree(tmp); }
    }

    static Path resolve(Path staging, String produced) {
        var p = Path.of(produced);
        if (Files.exists(p)) return p;
        var q = staging.resolve(p.getFileName());
        if (Files.exists(q)) return q;
        throw new IllegalStateException("backup reported " + produced + " but no such file exists");
    }

    static List<Path> archives(Path dest) throws IOException {
        if (!Files.isDirectory(dest)) return List.of();
        try (var s = Files.list(dest)) {
            return s.filter(p -> p.getFileName().toString().startsWith(PREFIX)
                             && p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).toList();
        }
    }

    static List<Path> prune(Path dest, int keep) throws IOException {
        var all = archives(dest);
        return all.size() <= keep ? List.of() : all.subList(keep, all.size());
    }

    static void listArchives(Path dest) throws IOException {
        var found = archives(dest);
        if (found.isEmpty()) { Out.line("no archives at " + dest); return; }
        Out.line("archives at " + dest);
        for (var p : found)
            Out.line("  %-46s %,10.1f MB  %s", p.getFileName(),
                Files.size(p) / 1048576.0, Files.getLastModifiedTime(p).toInstant());
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) { }
            });
        }
    }
}
