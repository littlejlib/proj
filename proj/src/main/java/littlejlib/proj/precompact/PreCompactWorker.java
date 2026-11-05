package littlejlib.proj.precompact;

import module java.base;

import com.google.gson.Gson;
import littlejlib.proj.hib.*;
import littlejlib.proj.prp.PrpEntry;

import static littlejlib.proj.precompact.PreCompactState.*;

/**
 * What happens after an automatic compaction was blocked: status update by the conversation's own claude,
 * logs and instructions, a record that this tab was parked by the hook, then the Tabs hibernate.
 * Nothing is ever killed that was not matched by session id.
 */
public final class PreCompactWorker {

    private PreCompactWorker() { }

    static final long IDLE_WAIT_S = 20 * 60;

    public static void run(String session) {
        try {
            work(session);
        } catch (RuntimeException e) {
            log(session + "  worker failed: " + e);
        } finally {
            release(session);
        }
    }

    static void work(String session) {
        var in = read(session);
        if (in == null) { log(session + "  no job file"); return; }
        var tab = Tabs.byClaudeSession(session);
        if (tab.isEmpty()) { log(session + "  not in Tabs: compaction blocked, nothing hibernated"); return; }
        if (StatusUpdate.waitIdle(session, IDLE_WAIT_S).isEmpty()) { log(session + "  never went idle: nothing hibernated"); return; }

        var cwd = Path.of(in.cwd());
        var transcript = in.transcriptPath() == null ? null : Path.of(in.transcriptPath());
        var prp = prpOf(in, tab.get(), transcript);
        String logFile = null;
        if (prp == null) log(session + "  no PRP (or not sure which): hibernating without status update or logs");
        else {
            var prpDir = prp.getMainFile().getParent();
            StatusUpdate.run(session, tab.get().id(), prpDir.resolve(prp.getIndex() + "-prp.status.md"));
            logFile = ProjCall.run(cwd, "prp", "logs", prp.getIndex(), "--dir", prpDir.toString(), "--session", session);
            var instr = ProjCall.run(cwd, "prp", "instructions", prp.getIndex(), "--dir", prpDir.toString(), "--session", session);
            log(session + "  logs " + (logFile == null ? "FAILED" : logFile) + "; instructions " + (instr == null ? "none new or failed" : "done"));
        }
        park(in, tab.get(), prp, logFile, transcript);
    }

    /** Recorded first: the worker may not outlive the hibernate it asks for. */
    static void park(PreCompactInput in, TabInfo tab, PrpEntry prp, String logFile, Path transcript) {
        var r = new HibRecord().sessionId(in.sessionId()).name(tab.claudeName()).cwd(in.cwd()).state(HibRecord.HIBERNATED)
                .terminal("Tabs").hibernatedBy(HibRecord.BY_PRECOMPACT).hibernatedAt(System.currentTimeMillis())
                .permissionMode(transcript == null ? null : Restorer.lastPermissionMode(transcript));
        if (prp != null) r.prpFile(prp.getMainFile().toString()).prpIndex(prp.getIndex()).prpName(prp.getName());
        if (logFile != null) r.logFile(logFile);
        HibStore.append(r);
        var ok = Tabs.hibernate(tab.id());
        log(in.sessionId() + "  tab " + tab.id() + (ok ? " hibernated" : " COULD NOT BE HIBERNATED"));
        if (!ok) HibStore.append(r.state(HibRecord.RESTORED).note("hibernate failed"));
    }

    static PrpEntry prpOf(PreCompactInput in, TabInfo tab, Path transcript) {
        if (transcript == null || !Files.exists(transcript)) return null;
        var live = new LiveSession().sessionId(in.sessionId()).cwd(in.cwd()).name(tab.claudeName());
        var pick = SessionPrp.pick(live, transcript);
        return pick != null && pick.sure() ? pick.entry() : null;
    }

    static PreCompactInput read(String session) {
        try {
            return new Gson().fromJson(Files.readString(job(session), StandardCharsets.UTF_8), PreCompactInput.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
