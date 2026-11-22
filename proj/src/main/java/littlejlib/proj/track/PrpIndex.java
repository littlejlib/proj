package littlejlib.proj.track;

import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpScanner;
import littlejlib.proj.prp.PrpStatus;
import littlejlib.proj.prp.PrpUuid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code prp/index.jsonl}: one line per PRP, built from the PRP files, meta.json, the timelines and the
 * work plan. A cache: rebuilt whole, never edited, never the only place anything lives. Reads only:
 * a PRP without a uuid gets an empty one here and is reported, it is not written into.
 */
public final class PrpIndex {

    private PrpIndex() { }

    static final Map<String, String> REVERSE = Map.of("parent", "child", "child", "parent", "supersedes", "superseded-by",
            "superseded-by", "supersedes", "depends-on", "blocks", "blocks", "depends-on", "reports-to", "reported-to-by",
            "sibling", "sibling", "related", "related");

    public static Path file(Path prpDir) {
        return prpDir.resolve("index.jsonl");
    }

    public static List<IndexLine> build(Path prpDir) throws IOException {
        var open = new HashMap<String, Integer>();
        for (var i : Workplan.current(prpDir).values())
            if (i.prp() != null && WorkplanCheck.LIVE.contains(i.status())) open.merge(i.prp(), 1, Integer::sum);
        var lines = new ArrayList<IndexLine>();
        for (var e : PrpCreator.list(prpDir)) lines.add(line(prpDir, e, open));
        addReverse(lines);
        return lines;
    }

    static IndexLine line(Path prpDir, PrpEntry e, Map<String, Integer> open) throws IOException {
        var uuid = PrpUuid.read(e.getMainFile());
        var meta = MetaFile.read(prpDir, e.getIndex());
        var notes = Timeline.read(prpDir, e.getIndex());
        var state = Timeline.state(notes);
        var related = meta == null || meta.related() == null ? new ArrayList<Relation>() : new ArrayList<>(meta.related());
        var newest = Math.max(e.getLastModified(), Math.max(PrpScanner.mtime(Timeline.file(prpDir, e.getIndex())),
                PrpScanner.mtime(MetaFile.file(prpDir, e.getIndex()))));
        return new IndexLine().uuid(uuid == null ? "" : uuid).number(e.getIndex()).name(e.getName())
                .file(e.getMainFile().getFileName().toString()).state(e.getStatus() == PrpStatus.CLOSED ? "closed" : "open")
                .summary(meta == null ? null : meta.summary()).related(related)
                .progress(state == null ? null : state.kind()).progressAt(state == null ? null : state.at())
                .notes(notes.size()).openItems(uuid == null ? 0 : open.getOrDefault(uuid, 0)).updated(iso(newest))
                .was(meta == null ? null : meta.was());
    }

    /** What B says about A is shown on A too, marked derived, unless A already says it. */
    static void addReverse(List<IndexLine> lines) {
        var byUuid = new HashMap<String, IndexLine>();
        for (var l : lines) if (!l.uuid().isEmpty()) byUuid.put(l.uuid(), l);
        for (var l : lines) for (var r : List.copyOf(l.related())) {
            var other = byUuid.get(r.prp());
            if (r.derived() != null || other == null) continue;
            if (other.related().stream().noneMatch(x -> l.uuid().equals(x.prp())))
                other.related().add(new Relation().prp(l.uuid()).kind(REVERSE.getOrDefault(r.kind(), r.kind())).note(r.note()).derived(true));
        }
        for (var l : lines) if (l.related().isEmpty()) l.related(null);
    }

    static String iso(long millis) {
        return millis <= 0 ? null : OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static void write(Path prpDir, List<IndexLine> lines) throws IOException {
        var f = file(prpDir);
        Jsonl.locked(f, () -> {
            var tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, lines.stream().map(l -> Jsonl.GSON.toJson(l) + "\n").collect(Collectors.joining()),
                    StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            return null;
        });
    }

    public static List<IndexLine> read(Path prpDir) throws IOException {
        return Jsonl.read(file(prpDir), IndexLine.class);
    }
}
