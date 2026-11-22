package littlejlib.proj.track;

import littlejlib.proj.prp.ProjFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Projects named the way people and sessions name them: uuid, short name, 3- or 4-letter alias, or path.
 * The list comes from proj's machine-wide PRP index; a name it does not know makes it walk the folders once more.
 * Two matches is an error naming both, never a guess.
 */
public final class ProjectScope {

    private ProjectScope() { }

    public record Project(String name, String uuid, Path dir, String alias3, String alias4) {
        public boolean hasWorkplan() {
            return Files.isRegularFile(Workplan.file(dir.resolve("prp")));
        }
    }

    public static List<Project> known() throws IOException {
        return fromIndex(GlobalIndex.read());
    }

    static List<Project> fromIndex(List<GlobalLine> lines) {
        var byDir = new LinkedHashMap<String, Project>();
        for (var l : lines) {
            if (l.project() == null || byDir.containsKey(l.project())) continue;
            var dir = Path.of(l.project());
            var uuid = l.projectUuid();
            var name = l.key();
            if (uuid == null || name == null) {
                var pj = ProjFile.read(dir);
                if (pj != null) {
                    if (uuid == null) uuid = pj.uuid();
                    if (name == null) name = pj.name();
                }
            }
            byDir.put(l.project(), new Project(name != null ? name : dir.getFileName().toString(), uuid, dir, l.alias3(), l.alias4()));
        }
        return new ArrayList<>(byDir.values());
    }

    /** One project for a uuid, name, alias or path; walks the folders again once when nothing matches. */
    public static Project resolve(String token) throws IOException {
        var seen = RESOLVED.get(token.strip().toLowerCase(Locale.ROOT));
        if (seen != null && Files.isDirectory(seen.dir())) return seen;
        var hit = match(token, known());
        if (hit == null) hit = match(token, fromIndex(GlobalIndex.rebuild(List.of())));
        if (hit == null) throw new IllegalArgumentException("no project named " + token + " (uuid, short name, alias or path)");
        RESOLVED.put(token.strip().toLowerCase(Locale.ROOT), hit);
        RESOLVED.put(hit.name().toLowerCase(Locale.ROOT), hit);
        if (hit.uuid() != null) RESOLVED.put(hit.uuid(), hit);
        return hit;
    }

    /** What this process has resolved already: a project outside the index is walked for once, not on every request. */
    static final Map<String, Project> RESOLVED = new java.util.concurrent.ConcurrentHashMap<>();

    static Project match(String token, List<Project> known) {
        var t = token.strip();
        for (var p : known) if (t.equalsIgnoreCase(p.uuid())) return p;
        var named = known.stream().filter(p -> t.equalsIgnoreCase(p.name()) || t.equalsIgnoreCase(p.alias3()) || t.equalsIgnoreCase(p.alias4())).toList();
        if (named.size() > 1) throw new IllegalArgumentException(token + " names " + named.size() + " projects: "
                + String.join(", ", named.stream().map(p -> p.dir() + " (uuid " + p.uuid() + ")").toList()) + ". Use the uuid or the path.");
        if (named.size() == 1) return named.get(0);
        try {
            var asPath = Path.of(t).toAbsolutePath().normalize();
            for (var p : known) if (p.dir().toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT)
                    .equals(asPath.toString().toLowerCase(Locale.ROOT))) return p;
            if (Files.isDirectory(asPath.resolve("prp"))) {
                var pj = ProjFile.read(asPath);
                return new Project(pj != null && pj.name() != null ? pj.name() : asPath.getFileName().toString(),
                        pj == null ? null : pj.uuid(), asPath, pj == null ? null : pj.alias3(), pj == null ? null : pj.alias4());
            }
        } catch (RuntimeException ignored) {
            // not a path
        }
        return null;
    }

    /** Short name to directory, in the order given, for {@link WorkGraph#load}. */
    public static Map<String, Path> dirs(List<Project> projects) {
        var out = new LinkedHashMap<String, Path>();
        for (var p : projects) out.put(p.name(), p.dir());
        return out;
    }
}
