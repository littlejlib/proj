package littlejlib.proj.cct.cli;

import module java.base;

import picocli.CommandLine.*;
import littlejlib.proj.cct.db.Db;

/**
 * Backup of the transcript index: a zip of its folder, taken while holding the index lock so no
 * indexing pass writes during it.
 *
 * <p>Why this index is worth backing up at all, when it looks derived: {@code TurnDoc} stores the
 * message {@code text}, and indexing treats Claude's replies and thinking blocks as opt-OUT
 * ({@code --no-assistant}, {@code --no-thinking}). claude-code prunes transcript .jsonl files off
 * disk over time, so for every session pruned since it was indexed, this index holds the only
 * surviving copy. It is a derived index whose source is being deleted underneath it.
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
            var db = Db.get();
            var zip = staging.resolve(name);
            var before = db.indexing(() -> zipDir(db.dir(), zip));
            Out.line("files         : %,d", before.size());
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

    /** Zips every file under dir; answers relative name to size. */
    static Map<String, Long> zipDir(Path dir, Path zip) {
        var sizes = new TreeMap<String, Long>();
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip)); var s = Files.walk(dir)) {
            for (var p : s.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().endsWith(".lock")).toList()) {
                var rel = dir.relativize(p).toString().replace('\\', '/');
                out.putNextEntry(new java.util.zip.ZipEntry(rel));
                sizes.put(rel, Files.copy(p, out));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sizes;
    }

    /** Reads the archive back and compares every entry's size with what was written. */
    static void verify(Path zip, Map<String, Long> expected) throws Exception {
        var actual = new TreeMap<String, Long>();
        try (var in = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            for (java.util.zip.ZipEntry e; (e = in.getNextEntry()) != null; ) actual.put(e.getName(), in.transferTo(OutputStream.nullOutputStream()));
        }
        if (!actual.equals(expected))
            throw new IllegalStateException("verification FAILED: archive holds " + actual.size() + " files, expected " + expected.size());
        Out.line("verify        : read the archive back, %,d files match", actual.size());
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
