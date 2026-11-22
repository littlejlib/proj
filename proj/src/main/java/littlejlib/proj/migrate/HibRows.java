package littlejlib.proj.migrate;

import module java.base;

import littlejlib.proj.hib.HibRecord;
import littlejlib.proj.hib.HibStore;

/** Hibernated conversations ({@code hibernated.jsonl}, append-only, last line per session wins) moved to the new PRP names. */
public final class HibRows {

    private HibRows() { }

    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static List<Move> plan(Rewriter rw, List<HibRecord> all) {
        return all.stream().map(rw::forHib).filter(m -> m != null && m.changed()).toList();
    }

    static void set(HibRecord r, Move m) {
        r.prpFile(m.toFile()).prpIndex(m.toIndex());
        if (r.name() != null) r.name(m.toName());
        if (m.toLog() != null) r.logFile(m.toLog());
    }

    /** Appends one updated line per move after copying the file aside once; returns how many were appended. */
    public static int apply(List<Move> moves, List<HibRecord> all) throws IOException {
        if (moves.isEmpty()) return 0;
        backup();
        var done = 0;
        for (var m : moves) {
            var r = all.stream().filter(x -> m.id().equals(x.sessionId())).findFirst().orElse(null);
            if (r == null) continue;
            set(r, m);
            HibStore.append(r);
            done++;
        }
        return done;
    }

    static void backup() throws IOException {
        var f = HibStore.file();
        if (!Files.exists(f)) return;
        var copy = f.resolveSibling(f.getFileName() + ".before-renumber-" + LocalDateTime.now().format(STAMP));
        if (!Files.exists(copy)) Files.copy(f, copy);
    }
}
