package littlejlib.proj.prp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * {@code <project>/prp/proj.json}, the source of truth for a project's short name. The index's
 * {@link ProjectKey} row is only a cache of it, kept so names can be checked for clashes.
 * <p>
 * Reading never writes: lists call this for every project they show. Writing happens when something
 * is launched, edited, or asked for explicitly through {@link #ensure}.
 */
public final class ProjFile {

    private ProjFile() { }

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static Path file(Path project) {
        return PrpCreator.prpDirOf(project).resolve("proj.json");
    }

    /** What is on disk, or null when there is no readable file. */
    public static ProjJson read(Path project) {
        try {
            var f = file(project);
            return Files.isRegularFile(f)
                    ? GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ProjJson.class) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static String nameOf(Path project) {
        var p = read(project);
        return p == null || p.name() == null || p.name().isBlank() ? null : p.name();
    }

    /**
     * The file's content with every missing value inferred and written: a uuid, a name (the key the
     * index already held, else the folder's name made unique), and nothing for the icon, which is
     * resolved from the git remote when it is first needed.
     */
    public static ProjJson ensure(Path project) {
        var p = read(project);
        if (p == null) p = new ProjJson();
        var changed = false;
        if (!ProjId.valid(p.uuid())) {
            p.uuid(ProjId.mint());
            changed = true;
        }
        if (p.name() == null || p.name().isBlank()) {
            p.name(inferName(project));
            changed = true;
        }
        if (changed || !Files.isRegularFile(file(project))) save(project, p);
        return p;
    }

    public static void update(Path project, Consumer<ProjJson> change) {
        var p = ensure(project);
        change.accept(p);
        save(project, p);
        PrpSessions.cacheKey(project, p.name());
    }

    public static void save(Path project, ProjJson p) {
        try {
            Files.createDirectories(file(project).getParent());
            Files.writeString(file(project), GSON.toJson(p) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file(project), e);
        }
    }

    /** Everything a launch needs to be in place: the file, the cache row and the icon. */
    public static ProjJson prepare(Path project) {
        var p = ensure(project);
        PrpSessions.cacheKey(project, p.name());
        ProjIcon.refresh(project, p, false);
        return p;
    }

    static String inferName(Path project) {
        var wanted = PrpSessions.dbKey(project);
        var base = wanted != null ? wanted : TabName.shortenProject(project);
        var name = base;
        for (var n = 2; PrpSessions.keyOwner(name, project) != null; n++) name = base + n;
        return name;
    }
}
