package littlejlib.proj.renumber;

import littlejlib.proj.track.Jsonl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;

/**
 * Every folder's mapping known to one run: the folders being renumbered, the ones refused (their names stay), and the
 * ones renumbered earlier (their {@code prp/renumber-map.jsonl}, also read on demand for an absolute path outside the run).
 */
final class RunRefs {

    final Map<Path, FolderRefs> folders = new LinkedHashMap<>();
    final Map<Path, Optional<FolderRefs>> loaded = new HashMap<>();

    void add(FolderRefs f) {
        folders.put(f.dir, f);
    }

    /** A folder renumbered earlier, from its map; null when it has none. */
    static FolderRefs fromMap(Path prpDir) {
        var map = prpDir.resolve(Renumber.MAP);
        if (!Files.isRegularFile(map)) return null;
        try {
            return new FolderRefs(prpDir, Renumber.self(prpDir.toAbsolutePath().normalize().getParent()), Jsonl.read(map, RenumberFile.class), false);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The folder a path names: {@code dir} is the text before {@code prp/}, with forward slashes. Empty means the
     * referring folder; an absolute path must match exactly; a relative one is matched by its ending, nearest first.
     */
    FolderRefs resolve(String dir, FolderRefs from) {
        try {
            return find(dir, from);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    FolderRefs find(String dir, FolderRefs from) {
        var path = dir.replaceAll("^(\\./)+", "").replaceAll("/+$", "");
        if (path.isEmpty()) return from;
        if (!path.contains("/") && from.self.contains(path.toLowerCase(Locale.ROOT))) return from;
        if (path.matches("^/[A-Za-z]/.*")) return absolute(path.charAt(1) + ":" + path.substring(2));
        if (path.matches("^[A-Za-z]:.*") || path.startsWith("/")) return absolute(path);
        if (path.startsWith("~/")) return absolute(System.getProperty("user.home") + path.substring(1));
        for (var base : List.of(from.dir, from.dir.getParent())) {
            var f = absolute(slashed(base) + "/" + path);
            if (f != null) return f;
            if (base.resolve(path).resolve("prp").normalize().equals(from.dir)) return from;
        }
        var tail = "/" + path.replaceAll("^(\\.\\./)+", "").toLowerCase(Locale.ROOT) + "/prp";
        if (tail.equals("/prp")) return null;
        FolderRefs best = null;
        var bestShared = -1;
        for (var f : folders.values()) {
            if (!slashed(f.dir).toLowerCase(Locale.ROOT).endsWith(tail)) continue;
            var shared = shared(f.dir, from.dir);
            if (shared > bestShared) {
                best = f;
                bestShared = shared;
            }
        }
        if (best != null) return best;
        var candidate = from.dir.getParent();
        for (var up = candidate; up != null; up = up.getParent()) {
            var f = absolute(slashed(up) + "/" + path);
            if (f != null) return f;
        }
        return null;
    }

    FolderRefs absolute(String path) {
        try {
            var p = Path.of(path.replaceAll("/+$", "")).resolve("prp").toAbsolutePath().normalize();
            var known = folders.get(p);
            if (known != null) return known;
            return loaded.computeIfAbsent(p, k -> Optional.ofNullable(fromMap(k))).orElse(null);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** Folders whose project is called {@code name} (folder name or proj.json name), when exactly one is. */
    FolderRefs byProjectName(String name) {
        FolderRefs found = null;
        for (var f : folders.values()) {
            if (!f.self.contains(name)) continue;
            if (found != null) return null;
            found = f;
        }
        return found;
    }

    Set<String> nearby;

    /** True when a folder of this name sits beside or above any project of the run: a word that may name a project. */
    boolean folderName(String name) {
        if (nearby == null) {
            nearby = new HashSet<>();
            var listed = new HashSet<Path>();
            for (var f : folders.values()) {
                for (var up = f.dir.getParent(); up != null; up = up.getParent()) {
                    if (up.getFileName() != null) nearby.add(up.getFileName().toString().toLowerCase(Locale.ROOT));
                    var parent = up.getParent();
                    if (parent == null || !listed.add(parent) || parent.getNameCount() < 2) continue;
                    try (var children = Files.list(parent)) {
                        children.filter(Files::isDirectory).forEach(c -> nearby.add(c.getFileName().toString().toLowerCase(Locale.ROOT)));
                    } catch (IOException | RuntimeException e) {
                        // unreadable folder
                    }
                }
            }
        }
        return nearby.contains(name);
    }

    static String slashed(Path p) {
        return p.toString().replace('\\', '/');
    }

    static int shared(Path a, Path b) {
        var n = 0;
        while (n < a.getNameCount() && n < b.getNameCount() && a.getName(n).equals(b.getName(n))) n++;
        return n;
    }
}
