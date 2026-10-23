package littlejlib.proj.hib;

import module java.base;

import littlejlib.proj.cct.export.*;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpLogExport;

/** Export the log, record it, close the tab. Mechanical: no model reads anything. */
public final class Hibernator {

    private Hibernator() { }

    /** @param pendingClose set for {@code self}: the caller reports first, then calls {@link Closer#end} */
    public record Outcome(HibRecord record, List<String> lines, int exit, LiveSession pendingClose) {
        static Outcome refused(int exit, String... lines) {
            return new Outcome(null, List.of(lines), exit, null);
        }
    }

    public static final int REFUSED_BUSY = 6, REFUSED_NO_PRP = 7, NO_TRANSCRIPT = 8, REFUSED_UNSURE = 9;
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    /** @param self true for {@code !proj hib}: the close is left to the caller, after it has reported */
    public static Outcome run(LiveSession s, boolean self, boolean anyway, boolean force, boolean dryRun) {
        return run(s, self, anyway, force, dryRun, null);
    }

    /** @param prpWanted {@code --prp}: a PRP file or number that overrides the one detected, or null */
    public static Outcome run(LiveSession s, boolean self, boolean anyway, boolean force, boolean dryRun, String prpWanted) {
        if (!self && s.busy() && !force)
            return Outcome.refused(REFUSED_BUSY, s.label() + " is busy (mid-turn). --force to hibernate it anyway.");
        var transcript = s.transcript();
        if (transcript == null)
            return Outcome.refused(NO_TRANSCRIPT, "no transcript found for " + s.label() + " in " + s.cwd());
        var pick = prpWanted != null ? given(prpWanted, s) : SessionPrp.pick(s, transcript);
        if (prpWanted != null && pick == null)
            return Outcome.refused(REFUSED_NO_PRP, "--prp " + prpWanted + ": no such PRP (a PRP file, or a number in " + s.cwd() + "\\prp)");
        if (pick != null && !pick.sure() && !dryRun) {
            if (!anyway) return uncertain(s, pick);
            pick = null;
        }
        var prp = pick == null ? null : pick.entry();
        if (prp == null && !anyway) return Outcome.refused(REFUSED_NO_PRP, notOnPrp(s));

        var stats = SessionRead.of(transcript, 1).session();
        var r = new HibRecord().sessionId(s.sessionId()).name(s.name()).cwd(s.cwd())
                .state(HibRecord.HIBERNATED).version(s.version()).terminal(s.terminal())
                .permissionMode(Restorer.lastPermissionMode(transcript))
                .hibernatedAt(System.currentTimeMillis()).lastActivity(s.updatedAt())
                .humanTurns(stats.humanTurns()).workingSet(s.workingSet()).privateBytes(s.privateBytes());
        if (prp != null) r.prpFile(prp.getMainFile().toString()).prpIndex(prp.getIndex()).prpName(prp.getName());

        var lines = new ArrayList<String>();
        var prpLine = "prp         " + (prp == null ? "none - log goes to " + HibStore.looseLogs()
                : prp.getMainFile() + "  (" + pick.why() + "; --prp <file|NN> to choose another)");
        if (dryRun) {
            lines.add("would hibernate " + s.label() + "  (" + ProcMem.mb(s.workingSet()) + " MB RAM, "
                    + ProcMem.mb(s.privateBytes()) + " MB committed)");
            lines.add(prpLine);
            if (pick != null && !pick.table().isEmpty()) {
                lines.add("            score    where           mentions  PRP");
                pick.table().forEach(t -> lines.add("          " + t));
            }
            return new Outcome(r, lines, 0, null);
        }

        r.logFile(writeLog(s, prp, transcript).toString());
        HibStore.append(r);
        lines.add("hibernated  " + s.label());
        lines.add(prpLine);
        lines.add("log         " + r.logFile());
        lines.add("restore     proj hib restore " + restoreHandle(r));
        if (self) {
            lines.add("closing this tab now");
            return new Outcome(r, lines, 0, s);
        }
        var closed = Closer.end(s);
        lines.add(closed ? "tab closed" : "could not end it - close the tab by hand; the record is saved");
        return new Outcome(r, lines, closed ? 0 : 3, null);
    }

    static SessionPrp.Pick given(String wanted, LiveSession s) {
        var e = SessionPrp.given(wanted, Path.of(s.cwd()));
        return e == null ? null : new SessionPrp.Pick(e, "given with --prp", List.of());
    }

    static Path writeLog(LiveSession s, PrpEntry prp, Path transcript) {
        if (prp != null) {
            var prpDir = prp.getMainFile().getParent();
            return PrpLogExport.write(prpDir, prp, Path.of(s.cwd()), transcript, 20).file();
        }
        var stats = SessionRead.of(transcript, 2).session();
        var text = SessionLogMd.render(stats, TranscriptReader.read(transcript),
                LogHeading.plain("Hibernated conversation " + s.label()), 20);
        var name = LocalDateTime.now().format(STAMP) + "_" + safe(s.label()) + "_"
                + s.sessionId().substring(0, 8) + ".md";
        var target = HibStore.looseLogs().resolve(name);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + target, e);
        }
        return target;
    }

    /** Guessing would file the log under the wrong task, so the choice goes back to whoever asked. */
    static Outcome uncertain(LiveSession s, SessionPrp.Pick pick) {
        var lines = new ArrayList<String>();
        lines.add("NOT HIBERNATED: cannot tell which PRP " + s.label() + " is on. Candidates (score, where, mentions):");
        pick.table().stream().limit(4).forEach(t -> lines.add("   " + t));
        lines.add("Choose one:  proj hib --prp <PRP file>   (or  proj hib tab " + s.pid() + " --prp <PRP file>),  or --anyway without a PRP.");
        return new Outcome(null, lines, REFUSED_UNSURE, null);
    }

    static String notOnPrp(LiveSession s) {
        return "NOT HIBERNATED: this conversation (" + s.label() + ") is not on a PRP, so there is no PRP "
                + "folder for its log and nothing to say what the task was. Claude: create a PRP for the "
                + "work in progress with  proj prp new \"<name>\"  in " + s.cwd() + ", write the current "
                + "task and next steps into it, then the user runs  !proj hib  again. To hibernate now "
                + "without a PRP:  !proj hib --anyway  (log goes to " + HibStore.looseLogs() + ").";
    }

    static String restoreHandle(HibRecord r) {
        return r.name() != null && !r.name().isBlank() && !r.name().contains(" ")
                ? r.name() : r.sessionId().substring(0, 8);
    }

    static String safe(String s) {
        var t = s.replaceAll("[^A-Za-z0-9._-]+", "-");
        return t.length() > 40 ? t.substring(0, 40) : t;
    }
}
