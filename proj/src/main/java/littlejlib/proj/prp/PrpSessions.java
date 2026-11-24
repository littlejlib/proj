package littlejlib.proj.prp;

import littlejlib.proj.track.Timeline;

import java.nio.file.Path;
import java.util.List;

/**
 * The one way to reach {@link PrpSession} rows. They are a file in proj's data folder that any
 * process reads and writes directly, so there is no daemon to ask and no lock to wait for.
 * <p>
 * Every path degrades to a derived name and an empty list rather than failing: a launch must not be
 * stopped by not knowing what to call a tab.
 */
public final class PrpSessions {

    private PrpSessions() { }

    public static String tabName(Path prpFile) {
        // The PRP's own short name (front matter, PRP 001R) names a new tab; a name remembered from an earlier
        // launch is only the fallback for a file without one. Resuming a session keeps that session's own tab.
        if (PrpHeader.shortNameOf(prpFile) != null) return TabName.derive(prpFile, key(AgentLauncher.projectRootOf(prpFile)));
        return quiet(() -> new PrpSessionStore().tabName(prpFile), TabName.of(prpFile, null));
    }

    /**
     * The project's key - the {@code billing} of {@code billing-38}. Null when none is set. The name in
     * {@code prp/proj.json} wins; the stored row is what projects had before that file existed.
     */
    public static String key(Path project) {
        var named = ProjFile.nameOf(project);
        return named != null ? named : dbKey(project);
    }

    /** The stored key alone, ignoring {@code prp/proj.json}. */
    static String dbKey(Path project) {
        return quiet(() -> new ProjectKeyStore().of(project), null);
    }

    public static void setKey(Path project, String key) {
        if (key == null || key.isBlank()) return;
        var clean = key.trim();
        if (clean.equals(ProjFile.nameOf(project))) return;
        ProjFile.update(project, p -> p.name(clean));
    }

    /** Keeps the stored copy of the name in step with the file, so clashes can be found. */
    static void cacheKey(Path project, String key) {
        if (key == null || key.isBlank()) return;
        quiet(() -> {
            new ProjectKeyStore().set(project, key);
            return null;
        }, null);
    }

    /** Another project that already uses this name, or null. Only projects seen so far are known. */
    public static String keyOwner(String key, Path project) {
        return quiet(() -> new ProjectKeyStore().ownerOf(key, project), null);
    }

    public static List<PrpSession> of(Path prpFile) {
        return quiet(() -> new PrpSessionStore().of(prpFile), List.of());
    }

    /** Every recorded session whose PRP file sits in this folder (for the renumber migration); null when unreadable. */
    public static List<PrpSession> inFolder(Path prpDir) {
        return quiet(() -> new PrpSessionStore().inFolder(prpDir), null);
    }

    /** Points a row at a renumbered PRP; true when the row was found and changed. */
    public static boolean repoint(String sessionId, Path prpFile, String prpIndex, String tabName) {
        return quiet(() -> new PrpSessionStore().repoint(sessionId, prpFile, prpIndex, tabName), false);
    }

    public static PrpSession latest(Path prpFile) {
        var all = of(prpFile);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The PRP's timeline gets a "start" line the first time it meets this session; a failure never stops a launch. */
    static void noteStart(String sessionId, Path prpFile, String prpIndex, String tabName) {
        try {
            var prpDir = prpFile.toAbsolutePath().normalize().getParent();
            if (prpDir == null || prpIndex == null) return;
            if (Timeline.read(prpDir, prpIndex).stream().anyMatch(n -> sessionId.equals(n.session()))) return;
            var uuid = PrpUuid.ensureQuiet(prpFile);
            Timeline.add(prpDir, prpIndex, uuid, "start", "session started" + (tabName == null || tabName.isBlank() ? "" : " in tab " + tabName),
                    null, sessionId);
        } catch (Exception e) {
            System.err.println("[PrpSessions] timeline: " + e.getMessage());
        }
    }

    public static void record(AgentLauncher.Launch plan) {
        record(plan.sessionId(), plan.prpFile(), PrpPrompt.indexOf(plan.prpFile()),
                plan.projectDir(), plan.tabName(), plan.exe());
    }

    public static void record(String sessionId, Path prpFile, String prpIndex, Path project,
                              String tabName) {
        record(sessionId, prpFile, prpIndex, project, tabName, null);
    }

    /** @param exe the explicit program the session runs on (fclaude ...), or null for the default */
    public static void record(String sessionId, Path prpFile, String prpIndex, Path project,
                              String tabName, String exe) {
        if (sessionId == null || sessionId.isBlank()) return;
        // Typing a tab name IS how the project's key gets set - and retyping a different one is how
        // it gets corrected, so this overwrites rather than filling a blank.
        learnKey(project, prpIndex, tabName);
        noteStart(sessionId, prpFile, prpIndex, tabName);
        PrpHead.onWork(prpFile, "start");           // a session started or resumed on it: a key moment for the head (PRP 001R)
        var stamp = System.currentTimeMillis();
        quiet(() -> {
            new PrpSessionStore().record(sessionId, prpFile, prpIndex, project, tabName, stamp, exe);
            return null;
        }, null);
    }

    static void learnKey(Path project, String prpIndex, String tabName) {
        var part = TabName.projectPartOf(tabName, prpIndex);
        if (part != null && !part.isBlank()) setKey(project, part);
    }

    /**
     * Degrades rather than throws - a launch must not be stopped by not knowing what to call a tab -
     * but SAYS SO. A write that vanishes silently is a resume that will later find nothing and no
     * way to know why.
     */
    static <T> T quiet(java.util.concurrent.Callable<T> work, T fallback) {
        try {
            return work.call();
        } catch (Exception e) {
            System.err.println("[PrpSessions] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return fallback;
        }
    }
}
