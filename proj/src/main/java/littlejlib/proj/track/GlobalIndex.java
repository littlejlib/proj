package littlejlib.proj.track;

import littlejlib.proj.prp.PrpDirFinder;
import littlejlib.proj.prp.ProjFile;
import littlejlib.proj.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every PRP proj can find, in one file in proj's own folder ({@code prp-global.jsonl}). A cache of a
 * cache: rebuilt by walking the roots, never written into any project, safe to delete.
 */
public final class GlobalIndex {

    private GlobalIndex() { }

    /** A folder to walk and how many levels below it projects may sit. */
    public record Root(Path dir, int depth) { }

    public static Path file() {
        return AppPaths.home().resolve("prp-global.jsonl");
    }

    public static List<GlobalLine> build(List<Root> roots) {
        var dirs = new LinkedHashSet<Path>();
        for (var r : roots) {
            try {
                var t = System.currentTimeMillis();
                var found = Files.isDirectory(r.dir()) ? PrpDirFinder.find(r.dir(), r.depth()) : List.<Path>of();
                dirs.addAll(found);
                System.err.println("[prp find] " + r.dir() + " (depth " + r.depth() + "): " + found.size() + " prp folders in "
                        + (System.currentTimeMillis() - t) / 1000 + " s");
            } catch (IOException | RuntimeException e) {
                System.err.println("[prp find] skipped " + r.dir() + ": " + e.getMessage());
            }
        }
        var out = new ArrayList<GlobalLine>();
        var t = System.currentTimeMillis();
        for (var prpDir : dirs) {
            try {
                var project = prpDir.getParent();
                var pj = ProjFile.read(project);
                var key = pj == null || pj.name() == null || pj.name().isBlank() ? null : pj.name();
                var a3 = pj == null ? null : pj.alias3();
                var a4 = pj == null ? null : pj.alias4();
                for (var l : PrpIndex.build(prpDir))
                    out.add(new GlobalLine().project(project.toString()).projectUuid(pj == null ? null : pj.uuid()).key(key).alias3(a3).alias4(a4)
                            .number(l.number()).uuid(l.uuid())
                            .name(l.name()).file(prpDir.resolve(l.file()).toString()).state(l.state())
                            .summary(l.summary()).progress(l.progress()).updated(l.updated()).was(l.was()));
            } catch (IOException | RuntimeException e) {
                System.err.println("[prp find] skipped " + prpDir + ": " + e.getMessage());
            }
        }
        System.err.println("[prp find] read " + out.size() + " PRPs in " + (System.currentTimeMillis() - t) / 1000 + " s");
        return out;
    }

    public static void write(List<GlobalLine> lines) throws IOException {
        var f = file();
        Files.createDirectories(f.getParent());
        Jsonl.locked(f, () -> {
            var tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, lines.stream().map(l -> Jsonl.GSON.toJson(l) + "\n").collect(Collectors.joining()), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            return null;
        });
    }

    /** Walks the configured group bases (plus {@code extra}) again and rewrites the cache. */
    public static List<GlobalLine> rebuild(List<Root> extra) throws IOException {
        var list = new ArrayList<Root>();
        for (var g : littlejlib.proj.group.Groups.of(new littlejlib.proj.SettingsManager().loadSettings()))
            for (var base : g.existingBasePaths()) list.add(new Root(base, g.depth()));
        list.addAll(extra);
        var lines = build(list);
        write(lines);
        System.err.println("indexed " + lines.size() + " PRPs from " + list.size() + " folders");
        return lines;
    }

    public static List<GlobalLine> read() throws IOException {
        return Jsonl.read(file(), GlobalLine.class);
    }

    /** Minutes since the cache was written; a large number when there is none. */
    public static long ageMinutes() {
        try {
            return (System.currentTimeMillis() - Files.getLastModifiedTime(file()).toMillis()) / 60_000;
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }
}
