package littlejlib.proj.track;

import littlejlib.proj.prp.PrpFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * A PRP's timeline, {@code NN-prp.status.jsonl}: append-only, one {@link Note} per line, each naming
 * the session that wrote it. It replaces writing {@code NN-prp.status.md}; old status files stay as history.
 */
public final class Timeline {

    private Timeline() { }

    public static final int MAX_TEXT = 280;

    public static final List<String> KINDS = List.of("note", "decision", "scope", "question", "handover",
            "built", "tested", "confirmed", "stalled", "closed", "start", "resume", "log");

    /** Kinds that say where the PRP stands; the newest of them is its state. */
    public static final Set<String> STATES = Set.of("built", "tested", "confirmed", "stalled", "closed");

    public static Path file(Path prpDir, String index) {
        return PrpFiles.timeline(prpDir, index);
    }

    public static Note add(Path prpDir, String index, String uuid, String kind, String text,
                           List<String> refs, String session) throws IOException {
        var k = kind == null || kind.isBlank() ? "note" : kind.strip().toLowerCase();
        if (!KINDS.contains(k)) throw new IllegalArgumentException("unknown kind " + k + "; one of " + String.join(", ", KINDS));
        var t = text == null ? "" : text.strip().replaceAll("\\s*\\R\\s*", " ");
        if (t.isEmpty()) throw new IllegalArgumentException("a note needs text");
        if (t.length() > MAX_TEXT) throw new IllegalArgumentException("the note is " + t.length() + " characters, the limit is "
                + MAX_TEXT + ": write the detail into a PRP sub-file and name it with --ref");
        var note = new Note().at(Now.iso()).prp(uuid).session(session).kind(k).text(t)
                .refs(refs == null || refs.isEmpty() ? null : refs);
        Jsonl.append(file(prpDir, index), note);
        return note;
    }

    public static List<Note> read(Path prpDir, String index) throws IOException {
        return Jsonl.read(file(prpDir, index), Note.class);
    }

    public static Note state(List<Note> notes) {
        for (var i = notes.size() - 1; i >= 0; i--) if (STATES.contains(notes.get(i).kind())) return notes.get(i);
        return null;
    }

    /** Where the default view starts: at the newest "confirmed", and at most {@code last} notes back. */
    public static int recentFrom(List<Note> notes, int last) {
        var from = Math.max(0, notes.size() - last);
        for (var i = notes.size() - 1; i >= from; i--) if ("confirmed".equals(notes.get(i).kind())) return i;
        return from;
    }

    /** The distinct sessions in order of first appearance: the n-th one is the PRP's cycle n. */
    public static List<String> sessions(List<Note> notes) {
        return notes.stream().map(Note::session).filter(s -> s != null && !s.isBlank()).distinct().toList();
    }
}
