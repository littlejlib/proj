package littlejlib.proj.cct.store;

import module java.base;

public final class FolderStats {

    private FolderStats() {}

    public static ProjectFolder of(Path dir) {
        var f = new ProjectFolder().encoded(dir.getFileName().toString()).path(dir.toString());
        try (var walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(p -> account(f, dir, p));
        } catch (IOException e) {
            // an unreadable subtree yields partial stats rather than no stats
        }
        return f;
    }

    private static void account(ProjectFolder f, Path root, Path p) {
        var size = 0L;
        var mtime = 0L;
        try {
            var a = Files.readAttributes(p, BasicFileAttributes.class);
            size = a.size();
            mtime = a.lastModifiedTime().toMillis();
        } catch (IOException e) {
            return;
        }
        if (mtime > f.lastModified()) f.lastModified(mtime);
        var rel = root.relativize(p).toString().replace('\\', '/');
        var name = p.getFileName().toString();
        if (name.endsWith(".jsonl")) {
            f.jsonlBytes(f.jsonlBytes() + size);
            if (rel.contains("/subagents/")) f.subagentFiles(f.subagentFiles() + 1);
            else f.sessionFiles(f.sessionFiles() + 1);
        } else if (rel.contains("/tool-results/")) {
            f.toolResultBytes(f.toolResultBytes() + size);
        } else if (rel.startsWith("memory/")) {
            f.memoryBytes(f.memoryBytes() + size);
            f.memoryFiles(f.memoryFiles() + 1);
        }
    }

    public static String cwdKey(String cwd) {
        return cwd == null ? null : cwd.replace('/', '\\').toLowerCase(Locale.ROOT);
    }
}
