package littlejlib.proj.cct.store;

import module java.base;

public final class StoreScanner {

    private StoreScanner() {}

    public static List<Path> projectDirs() {
        var root = StorePaths.projectsDir();
        if (!Files.isDirectory(root)) return List.of();
        try (var s = Files.list(root)) {
            return s.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static List<SessionFile> scan() {
        var out = new ArrayList<SessionFile>();
        for (var dir : projectDirs()) collectProject(dir, out);
        return out;
    }

    public static List<SessionFile> scanProject(Path dir) {
        var out = new ArrayList<SessionFile>();
        collectProject(dir, out);
        return out;
    }

    private static void collectProject(Path dir, List<SessionFile> out) {
        var encoded = dir.getFileName().toString();
        try (var s = Files.list(dir)) {
            for (var entry : s.toList()) {
                if (Files.isRegularFile(entry)) topLevel(entry, encoded, out);
                else if (Files.isDirectory(entry)) subagents(entry, encoded, out);
            }
        } catch (IOException e) {
            // a folder we cannot list is reported as empty rather than aborting the scan
        }
    }

    private static void topLevel(Path file, String encoded, List<SessionFile> out) {
        var name = file.getFileName().toString();
        if (!name.endsWith(".jsonl")) return;
        out.add(describe(file, encoded, name.substring(0, name.length() - 6), null, false));
    }

    private static void subagents(Path sessionDir, String encoded, List<SessionFile> out) {
        var parent = sessionDir.getFileName().toString();
        if ("memory".equals(parent)) return;
        var subs = sessionDir.resolve("subagents");
        if (!Files.isDirectory(subs)) return;
        try (var s = Files.list(subs)) {
            for (var f : s.toList()) {
                var name = f.getFileName().toString();
                if (!name.endsWith(".jsonl")) continue;
                out.add(describe(f, encoded, name.substring(0, name.length() - 6), parent, true));
            }
        } catch (IOException e) {
            // ignore an unreadable subagents folder
        }
    }

    private static SessionFile describe(Path f, String encoded, String id,
                                        String parentId, boolean sub) {
        var size = 0L;
        var mtime = 0L;
        try {
            var a = Files.readAttributes(f, BasicFileAttributes.class);
            size = a.size();
            mtime = a.lastModifiedTime().toMillis();
        } catch (IOException e) {
            // keep zeroes; the indexer treats a zero-size file as nothing to read
        }
        return new SessionFile()
            .path(f.toString())
            .projectEncoded(encoded)
            .sessionId(id)
            .parentSessionId(parentId)
            .subagent(sub)
            .sizeBytes(size)
            .lastModified(mtime);
    }
}
