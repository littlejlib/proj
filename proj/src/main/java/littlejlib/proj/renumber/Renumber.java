package littlejlib.proj.renumber;

import littlejlib.proj.prp.ProjFile;
import littlejlib.proj.track.Jsonl;
import littlejlib.proj.track.MetaFile;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** {@code proj prp renumber}: the shared pieces of a run (file names, labels, was/wasFile), and one folder alone. */
public final class Renumber {

    private Renumber() { }

    public static final String MAP = "renumber-map.jsonl", REVIEW = "renumber-review.jsonl";
    static final Set<String> TEXT = Set.of("md", "txt", "jsonl", "json"), SKIP = Set.of(MAP, REVIEW, "index.jsonl");

    /** One folder alone; a run over several folders is {@link RenumberRun#run}. */
    public static RenumberResult apply(Path prpDir, boolean dryRun) throws IOException {
        return RenumberRun.run(List.of(prpDir), dryRun).get(0);
    }

    static void recordWas(Path prpDir, RenumberPlan plan) throws IOException {
        for (var f : plan.files()) {
            if (!f.kind().equals("main")) continue;
            var old = MetaFile.read(prpDir, f.newNumber());
            var uuid = !f.uuid().isEmpty() ? f.uuid() : old == null ? null : old.uuid();
            MetaFile.update(prpDir, f.newNumber(), uuid, m -> m.was(f.oldNumber()).wasFile(f.oldFile()));
        }
    }

    /** Names that may stand before this project's own "PRP NN": its folder name and its proj.json name. */
    static Set<String> self(Path project) {
        var out = new HashSet<String>();
        if (project == null) return out;
        if (project.getFileName() != null) out.add(project.getFileName().toString().toLowerCase(Locale.ROOT));
        try {
            var name = ProjFile.nameOf(project);
            if (name != null && !name.isBlank()) out.add(name.toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            // no proj.json name
        }
        return out;
    }

    static List<Path> texts(Path prpDir, Path project) throws IOException {
        var out = new ArrayList<Path>();
        try (var files = Files.list(prpDir)) {
            files.filter(Files::isRegularFile).filter(f -> {
                var n = f.getFileName().toString();
                var dot = n.lastIndexOf('.');
                return dot > 0 && TEXT.contains(n.substring(dot + 1).toLowerCase(Locale.ROOT)) && !SKIP.contains(n);
            }).sorted().forEach(out::add);
        }
        var claude = project == null ? null : project.resolve("CLAUDE.md");
        if (claude != null && Files.isRegularFile(claude)) out.add(claude);
        return out;
    }

    static String read(Path f, String label, List<RenumberReview> reviews) throws IOException {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            reviews.add(new RenumberReview().file(label).line(0).text("").why("not UTF-8: references not rewritten"));
            return null;
        }
    }

    static String label(Path project, Path f) {
        return project == null ? f.getFileName().toString() : project.relativize(f.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    static void writeLines(Path file, List<?> lines) throws IOException {
        Files.writeString(file, lines.stream().map(l -> Jsonl.GSON.toJson(l) + "\n").collect(Collectors.joining()), StandardCharsets.UTF_8);
    }
}
