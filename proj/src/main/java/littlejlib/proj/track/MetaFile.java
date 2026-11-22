package littlejlib.proj.track;

import littlejlib.proj.prp.PrpFiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** {@code NN-prp.meta.json}: summary and relations of a PRP. Small, replaced whole (temp file, then move). */
public final class MetaFile {

    private MetaFile() { }

    public static final int MAX_SUMMARY = 200;

    public static final List<String> RELATIONS = List.of("parent", "child", "supersedes", "superseded-by",
            "depends-on", "blocks", "reports-to", "sibling", "related");

    static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static Path file(Path prpDir, String index) {
        return PrpFiles.meta(prpDir, index);
    }

    /** What is on disk, or null. */
    public static PrpMeta read(Path prpDir, String index) {
        var f = file(prpDir, index);
        try {
            return Files.isRegularFile(f) ? PRETTY.fromJson(Files.readString(f, StandardCharsets.UTF_8), PrpMeta.class) : null;
        } catch (IOException | RuntimeException e) {
            System.err.println(f.getFileName() + ": not readable, " + e.getMessage());
            return null;
        }
    }

    public static PrpMeta update(Path prpDir, String index, String uuid, Consumer<PrpMeta> change) throws IOException {
        var f = file(prpDir, index);
        return Jsonl.locked(f, () -> {
            var m = read(prpDir, index);
            if (m == null) m = new PrpMeta();
            m.uuid(uuid);
            m.related(m.related() == null ? new ArrayList<>() : new ArrayList<>(m.related()));
            change.accept(m);
            if (m.summary() != null && m.summary().length() > MAX_SUMMARY)
                throw new IllegalArgumentException("the summary is " + m.summary().length() + " characters, the limit is " + MAX_SUMMARY);
            if (m.related().isEmpty()) m.related(null);
            m.updated(Now.iso());
            var tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, PRETTY.toJson(m) + "\n", StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            return m;
        });
    }

    /** Sets or replaces the relation to {@code target} (one per target), or removes it when kind is null. */
    public static void relate(PrpMeta m, String target, String kind, String note) {
        if (kind != null && !RELATIONS.contains(kind))
            throw new IllegalArgumentException("unknown relation " + kind + "; one of " + String.join(", ", RELATIONS));
        m.related().removeIf(r -> target.equals(r.prp()));
        if (kind != null) m.related().add(new Relation().prp(target).kind(kind).note(note == null || note.isBlank() ? null : note.strip()));
    }
}
