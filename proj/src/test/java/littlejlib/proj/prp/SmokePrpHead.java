package littlejlib.proj.prp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * PRP 001R: prp_status, created_utc, last_worked_on_utc in the head; closed is a status, not a file name.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.prp.SmokePrpHead" -Dexec.classpathScope=test
 */
public class SmokePrpHead {

    static final String HEAD = """
            ---
            prp_number: 1
            prp_number_b34str: "0001"
            prp_name: export
            prp_3_letter_name: exp
            prp_short_name: export
            prp_uuid: 0al4a9rafajvsplsdzg5wyfeqx
            conventions: x
            ---

            # Requirement
            """;

    public static void main(String[] args) throws Exception {
        states();
        header();
        disk();
        closedMigration();
        System.out.println("ok");
        System.exit(0);
    }

    static void states() {
        check(PrpState.parse("in_progress") == PrpState.IN_PROGRESS && PrpState.parse("In-Review") == PrpState.IN_REVIEW, "parse");
        check(PrpState.parse("done") == null && PrpState.parse(null) == null && PrpState.parse("") == null, "outside the set");
        try {
            PrpState.require("done");
            check(false, "refused");
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains("cancelled"), "names the allowed values");
        }
        check(PrpState.CLOSED.status() == PrpStatus.CLOSED && PrpState.CANCELLED.status() == PrpStatus.CLOSED
                && PrpState.BLOCKED.status() == PrpStatus.ACTIVE && PrpState.NEW.status() == PrpStatus.ACTIVE, "status mapping");
        check(PrpHead.afterNote(PrpState.NEW, "note") == PrpState.IN_PROGRESS, "first note starts work");
        check(PrpHead.afterNote(null, "start") == PrpState.IN_PROGRESS && PrpHead.afterNote(PrpState.ON_HOLD, "resume") == PrpState.IN_PROGRESS, "start resumes");
        check(PrpHead.afterNote(PrpState.IN_PROGRESS, "built") == PrpState.IN_REVIEW, "built waits for the user");
        check(PrpHead.afterNote(PrpState.BLOCKED, "note") == PrpState.BLOCKED && PrpHead.afterNote(PrpState.IN_REVIEW, "log") == PrpState.IN_REVIEW, "notes do not unblock");
        check(PrpHead.afterNote(PrpState.IN_PROGRESS, "closed") == PrpState.CLOSED, "closed note closes");
        check(PrpHead.afterNote(PrpState.CLOSED, "start") == PrpState.CLOSED && PrpHead.afterNote(PrpState.CANCELLED, "note") == PrpState.CANCELLED, "finished stays");
        System.out.println("  pass  states: closed set, mapping, transitions");
    }

    static void header() {
        var h = PrpHeader.parse(HEAD.lines().toList());
        check(h.yaml && h.status == null && h.createdUtc == null, "old yaml head has no status");
        h.status = "in_review";
        h.createdUtc = "2026-11-22T06:35:00Z";
        h.lastWorkedOnUtc = "2026-11-22T09:10:00Z";
        var out = h.render();
        check(out.indexOf("prp_status: in_review") == out.indexOf("prp_uuid: 0al4a9rafajvsplsdzg5wyfeqx") + 1, "status follows the uuid: " + out);
        check(out.contains("created_utc: \"2026-11-22T06:35:00Z\"") && out.contains("last_worked_on_utc: \"2026-11-22T09:10:00Z\""), "times are quoted");
        check(out.indexOf("conventions: x") > out.indexOf("last_worked_on_utc: \"2026-11-22T09:10:00Z\""), "conventions stay last");
        var back = PrpHeader.parse(out);
        check(back.state() == PrpState.IN_REVIEW && "2026-11-22T06:35:00Z".equals(back.createdUtc) && back.extra.isEmpty(), "round trip");
        System.out.println("  pass  header: three keys after the uuid, times quoted, round trip");
    }

    static void disk() throws Exception {
        var dir = Files.createTempDirectory("smoke-prp-head");
        var f = dir.resolve("000100-export.md");
        Files.writeString(f, HEAD.replace("\n", "\r\n"));
        var want = new LinkedHashMap<String, String>();
        want.put("prp_status", "in_progress");
        want.put("created_utc", "\"2026-11-22T06:35:00Z\"");
        want.put("last_worked_on_utc", "\"2026-11-22T09:10:00Z\"");
        check(PrpHead.applyDisk(f, want, false), "applied");
        var text = Files.readString(f);
        check(text.contains("\r\n") && !text.replace("\r\n", "").contains("\n"), "line endings kept");
        var lines = List.of(text.split("\r\n", -1));
        var at = lines.indexOf("prp_uuid: 0al4a9rafajvsplsdzg5wyfeqx");
        check(lines.get(at + 1).equals("prp_status: in_progress") && lines.get(at + 2).startsWith("created_utc: ")
                && lines.get(at + 3).startsWith("last_worked_on_utc: ") && lines.get(at + 4).equals("conventions: x"), "keys in order: " + lines);
        check(text.endsWith("# Requirement\r\n"), "body untouched");
        // changing one line touches only that line
        var one = new LinkedHashMap<String, String>();
        one.put("last_worked_on_utc", "\"2026-11-22T10:00:00Z\"");
        PrpHead.applyDisk(f, one, false);
        var after = List.of(Files.readString(f).split("\r\n", -1));
        check(after.size() == lines.size() && after.get(at + 3).equals("last_worked_on_utc: \"2026-11-22T10:00:00Z\""), "one line replaced");
        check(PrpHead.plan(after, one).isEmpty(), "nothing to do twice");
        check(PrpHead.plan(List.of("# not a head"), one) == null, "no head, no plan");
        check(PrpHead.stateOf(f) == PrpState.IN_PROGRESS, "stateOf reads the head");
        Files.writeString(f, Files.readString(f).replace("prp_status: in_progress", "prp_status: done"));
        check(PrpHead.stateOf(f) == null, "a value outside the set is ignored");
        System.out.println("  pass  disk: keys inserted in order, one line replaced, endings and body kept");
    }

    static void closedMigration() throws Exception {
        var dir = Files.createTempDirectory("smoke-prp-closed");
        var prp = Files.createDirectories(dir.resolve("proj1").resolve("prp"));
        var old = prp.resolve("000100-export.closed.md");
        Files.writeString(old, HEAD);
        check(PrpHead.stateOf(old) == PrpState.CLOSED, "an old closed file name still reads as closed");
        var entry = PrpScanner.scanPrpDirectory(prp).get(0);
        check(entry.getStatus() == PrpStatus.CLOSED, "scanner: closed by name before migration");
        var up = new PrpUpgrade(java.util.Map.of(), dir.resolve("backup"));
        up.repoint = false;
        var dry = up.upgrade(old, true);
        check(dry.changed() && Files.exists(old), "dry run renames nothing: " + dry.now());
        var r = up.upgrade(old, false);
        var now = prp.resolve("000100-export.md");
        check(r.changed() && Files.exists(now) && !Files.exists(old), "renamed to .md");
        check(PrpHeader.of(now).state() == PrpState.CLOSED && Files.readString(now).endsWith("# Requirement\n"), "prp_status: closed, body kept");
        var again = PrpScanner.scanPrpDirectory(prp).get(0);
        check(again.getStatus() == PrpStatus.CLOSED && again.getState() == PrpState.CLOSED && again.getStateKey().equals("closed"), "scanner reads the head");
        // reopening
        PrpHead.setStatus(now, PrpState.IN_PROGRESS);
        check(PrpScanner.scanPrpDirectory(prp).get(0).getStatus() == PrpStatus.ACTIVE, "reopened without a rename");
        check(PrpHeader.of(now).createdUtc != null && PrpHeader.of(now).lastWorkedOnUtc != null, "setStatus adds the times");
        System.out.println("  pass  closed migration: .closed.md -> .md with prp_status: closed; scanner and setStatus read the head");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
