package littlejlib.proj.prp;

import littlejlib.proj.track.Timeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reading and changing the YAML head of a PRP main file after it exists (PRP 001R): {@code prp_status},
 * {@code created_utc}, {@code last_worked_on_utc}.
 * <p>
 * The keys are added LAZILY, when proj next touches a PRP, never in bulk. A change touches only the lines that change.
 * A file the person has open in notepadfx is never written on disk: the same change goes through notepadfx's own
 * verbs ({@link Notepadfx}), expecting the text it saw, so his unsaved typing and caret stay; when the text moved
 * meanwhile it reads again and retries once, then gives up quietly.
 * <p>
 * Nothing here throws at a caller: bookkeeping must never stop a launch or a note.
 */
public final class PrpHead {

    private PrpHead() { }

    /** {@code last_worked_on_utc} is written at most this often per PRP, unless the status changes. */
    public static final Duration THROTTLE = Duration.ofHours(1);

    static final int HEAD_LINES = 60;

    // --- reading -------------------------------------------------------------------------------------------

    record Cached(long mtime, long size, PrpState state) { }

    static final Map<Path, Cached> CACHE = new ConcurrentHashMap<>();

    /**
     * The PRP's state: the head's {@code prp_status}; for a file whose head has none, {@code closed} when the file
     * is still named {@code .closed.md}; else null (unknown, shown as open). Read once per file version.
     */
    public static PrpState stateOf(Path mainFile) {
        try {
            var attrs = Files.readAttributes(mainFile, java.nio.file.attribute.BasicFileAttributes.class);
            var mtime = attrs.lastModifiedTime().toMillis();
            var hit = CACHE.get(mainFile);
            if (hit != null && hit.mtime() == mtime && hit.size() == attrs.size()) return hit.state();
            var state = PrpHeader.of(mainFile).state();
            if (state == null && mainFile.getFileName().toString().endsWith(".closed.md")) state = PrpState.CLOSED;
            CACHE.put(mainFile, new Cached(mtime, attrs.size(), state));
            return state;
        } catch (IOException | RuntimeException e) {
            return mainFile.getFileName().toString().endsWith(".closed.md") ? PrpState.CLOSED : null;
        }
    }

    public static String utc(Instant t) {
        return java.time.format.DateTimeFormatter.ISO_INSTANT.format(t.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    static Instant parseUtc(String s) {
        try {
            return s == null || s.isBlank() ? null : Instant.parse(s.strip());
        } catch (RuntimeException e) {
            return null;
        }
    }

    // --- the key moments -----------------------------------------------------------------------------------

    /** What a timeline note of this kind does to the status of a PRP that stands at {@code now} (null = no head status yet). */
    public static PrpState afterNote(PrpState now, String kind) {
        if (now != null && now.finished()) return now;
        var k = kind == null ? "note" : kind;
        return switch (k) {
            case "closed" -> PrpState.CLOSED;
            case "built", "tested" -> now == null || now == PrpState.NEW || now == PrpState.IN_PROGRESS ? PrpState.IN_REVIEW : now;
            case "start", "resume" -> PrpState.IN_PROGRESS;
            case "log", "confirmed" -> now;
            default -> now == null || now == PrpState.NEW ? PrpState.IN_PROGRESS : now;
        };
    }

    /** Where the timeline says an existing PRP stands, for the PRP that has no {@code prp_status} yet. */
    static PrpState derive(Path prpDir, String index) {
        try {
            var notes = Timeline.read(prpDir, index);
            if (notes.isEmpty()) return PrpState.NEW;
            var state = Timeline.state(notes);
            if (state == null) return PrpState.IN_PROGRESS;
            return switch (state.kind()) {
                case "closed" -> PrpState.CLOSED;
                case "built", "tested" -> PrpState.IN_REVIEW;
                default -> PrpState.IN_PROGRESS;
            };
        } catch (IOException | RuntimeException e) {
            return PrpState.IN_PROGRESS;
        }
    }

    /**
     * A timeline note was just written on this PRP (or a session started or resumed on it): adds the keys the head lacks,
     * moves the status as {@link #afterNote} says, and stamps {@code last_worked_on_utc} (at most hourly).
     */
    public static void onWork(Path mainFile, String kind) {
        try {
            var prpDir = mainFile.toAbsolutePath().normalize().getParent();
            var index = PrpPrompt.indexOf(mainFile);
            var h = headOf(mainFile);
            if (!h.yaml) return;            // an old head is brought up by proj prp upgrade first
            var base = h.state() != null ? h.state() : index == null ? PrpState.IN_PROGRESS : derive(prpDir, index);
            var target = afterNote(base, kind);
            var want = new LinkedHashMap<String, String>();
            if (h.state() != target) want.put("prp_status", target.key());
            if (h.createdUtc == null) want.put("created_utc", quoted(created(mainFile, prpDir, index)));
            var last = parseUtc(h.lastWorkedOnUtc);
            var now = Instant.now();
            if (want.containsKey("prp_status") || last == null || Duration.between(last, now).compareTo(THROTTLE) >= 0)
                want.put("last_worked_on_utc", quoted(now));
            apply(mainFile, want, h.createdUtc == null || h.status == null);
        } catch (RuntimeException e) {
            System.err.println("[PrpHead] " + mainFile.getFileName() + ": " + e.getMessage());
        }
    }

    /** The head as it stands: the live text when the person has the file open in notepadfx (unsaved keys included), else the file. */
    static PrpHeader headOf(Path mainFile) {
        if (Notepadfx.isOpen(mainFile)) {
            var live = Notepadfx.readLines(mainFile, 1, HEAD_LINES);
            if (live != null) return PrpHeader.parse(live);
        }
        return PrpHeader.of(mainFile);
    }

    /** Sets the status (a key moment: {@code last_worked_on_utc} follows). Throws for a value outside the set or an old head. */
    public static void setStatus(Path mainFile, PrpState state) throws IOException {
        var h = headOf(mainFile);
        if (!h.yaml) throw new IOException(mainFile.getFileName() + " has an old head: run proj prp upgrade on it first");
        var want = new LinkedHashMap<String, String>();
        want.put("prp_status", state.key());
        if (h.createdUtc == null) {
            var prpDir = mainFile.toAbsolutePath().normalize().getParent();
            want.put("created_utc", quoted(created(mainFile, prpDir, PrpPrompt.indexOf(mainFile))));
        }
        want.put("last_worked_on_utc", quoted(Instant.now()));
        if (!apply(mainFile, want, h.createdUtc == null || h.status == null))
            throw new IOException("could not change the head of " + mainFile.getFileName() + " (open in notepadfx and changed meanwhile?)");
    }

    /** Closes an open PRP, reopens a finished one (back to in_progress); a file still named .closed.md is migrated first. Returns the new state. */
    public static PrpState toggleClosed(Path file) throws IOException {
        var fileName = file.getFileName().toString();
        if (fileName.endsWith(".closed.md")) {
            new PrpUpgrade(java.util.Map.of(), PrpUpgrade.newBackupDir()).upgrade(file, false);
            file = file.resolveSibling(fileName.replaceFirst("\\.closed\\.md$", ".md"));
            setStatus(file, PrpState.IN_PROGRESS);
            return PrpState.IN_PROGRESS;
        }
        var now = stateOf(file);
        var next = now != null && now.finished() ? PrpState.IN_PROGRESS : PrpState.CLOSED;
        setStatus(file, next);
        return next;
    }

    static String quoted(Instant t) {
        return "\"" + utc(t) + "\"";
    }

    /** The earliest of the file's creation time and its first timeline note. */
    static Instant created(Path mainFile, Path prpDir, String index) {
        Instant best = Instant.now();
        try {
            var born = Files.readAttributes(mainFile, java.nio.file.attribute.BasicFileAttributes.class).creationTime().toInstant();
            if (born.isBefore(best)) best = born;
        } catch (IOException | RuntimeException ignored) {
            // no creation time: the note or now will do
        }
        try {
            if (index != null) for (var n : Timeline.read(prpDir, index)) {
                var at = littlejlib.proj.track.Now.instant(n.at());
                if (at != null && at.isBefore(best)) best = at;
            }
        } catch (IOException | RuntimeException ignored) {
            // no timeline
        }
        return best;
    }

    // --- writing -------------------------------------------------------------------------------------------

    /**
     * Sets keys of the head, {@code key -> value text as written after "key: "}. Returns true when the file holds them
     * afterwards. {@code backup} keeps the previous file in prp-backups first (when keys are being added for the first time).
     */
    static boolean apply(Path file, Map<String, String> want, boolean backup) {
        if (want.isEmpty()) return true;
        try {
            if (Notepadfx.isOpen(file)) return applyOpen(file, want);
            return applyDisk(file, want, backup);
        } catch (IOException | RuntimeException e) {
            System.err.println("[PrpHead] " + file.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    /** One change to the head: replace line {@code at} (0-based) or insert after it. */
    record Op(boolean insert, int at, String expect, String text, int seq) { }

    /** The changes that bring {@code lines} (a head starting at line 0) to the wanted keys; null when it is not a head. */
    static List<Op> plan(List<String> lines, Map<String, String> want) {
        if (lines.isEmpty() || !lines.get(0).strip().equals("---")) return null;
        int close = -1;
        var at = new LinkedHashMap<String, Integer>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("---")) {
                close = i;
                break;
            }
            var m = PrpHeader.YAML_LINE.matcher(lines.get(i));
            if (m.matches()) at.putIfAbsent(m.group(1), i);
        }
        if (close < 0) return null;
        var ops = new ArrayList<Op>();
        // New keys keep the order of PrpHeader.KEYS: each goes after the nearest earlier key present, in the order added.
        var after = new LinkedHashMap<String, Integer>(at);
        int seq = 0;
        for (var e : want.entrySet()) {
            seq++;
            var line = e.getKey() + ": " + e.getValue();
            var have = at.get(e.getKey());
            if (have != null) {
                if (!lines.get(have).equals(line)) ops.add(new Op(false, have, lines.get(have), line, seq));
                continue;
            }
            int anchor = 0;
            var order = PrpHeader.KEYS.indexOf(e.getKey());
            for (var k : PrpHeader.KEYS.subList(0, Math.max(order, 0))) if (after.containsKey(k)) anchor = Math.max(anchor, after.get(k));
            if (anchor == 0) anchor = close - 1;
            ops.add(new Op(true, anchor, lines.get(anchor), line, seq));
            after.put(e.getKey(), anchor);
        }
        return ops;
    }

    static boolean applyDisk(Path file, Map<String, String> want, boolean backup) throws IOException {
        var text = Files.readString(file, StandardCharsets.UTF_8);
        var eol = text.contains("\r\n") ? "\r\n" : "\n";
        var lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        var ops = plan(lines.subList(0, Math.min(lines.size(), HEAD_LINES)), want);
        if (ops == null) throw new IOException("not YAML front matter");
        if (ops.isEmpty()) return true;
        for (var op : sortedDescending(ops)) {
            if (op.insert()) lines.add(op.at() + 1, op.text());
            else lines.set(op.at(), op.text());
        }
        if (backup) PrpFileEdit.backup(file);
        Files.writeString(file, String.join(eol, lines), StandardCharsets.UTF_8);
        return true;
    }

    static List<Op> sortedDescending(List<Op> ops) {
        var out = new ArrayList<>(ops);
        out.sort((a, b) -> a.at() != b.at() ? Integer.compare(b.at(), a.at())
                : a.insert() != b.insert() ? Boolean.compare(b.insert(), a.insert()) : Integer.compare(b.seq(), a.seq()));
        return out;
    }

    /** Through notepadfx: line by line from the bottom, expecting each line as read; one retry when the text moved. */
    static boolean applyOpen(Path file, Map<String, String> want) {
        for (int attempt = 0; attempt < 2; attempt++) {
            var lines = Notepadfx.readLines(file, 1, HEAD_LINES);
            if (lines == null) return false;
            var ops = plan(lines, want);
            if (ops == null) return false;
            if (ops.isEmpty()) return true;
            boolean stale = false;
            for (var op : sortedDescending(ops)) {
                var line = op.at() + 1;                      // notepadfx counts from 1
                int exit = op.insert() ? Notepadfx.insertAfter(file, line, op.expect(), op.text())
                        : Notepadfx.replaceLine(file, line, op.expect(), op.text());
                if (exit == Notepadfx.STALE) {
                    stale = true;
                    break;
                }
                if (exit != Notepadfx.OK) return false;
            }
            if (!stale) return true;
        }
        return false;
    }
}
