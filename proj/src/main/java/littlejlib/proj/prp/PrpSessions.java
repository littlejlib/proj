package littlejlib.proj.prp;

import littlejlib.proj.daemon.Client;
import littlejlib.proj.db.DB;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The one way to reach {@link PrpSession} rows, wherever the caller happens to be running.
 * <p>
 * ArcadeDB's lock is exclusive and the daemon normally holds it, so where the data lives depends on
 * who is asking. Already holding it open - the picker, the daemon - read it directly. Otherwise ask
 * the daemon, exactly as {@code proj list} does. Only with no daemon at all is it opened here.
 * <p>
 * Every path degrades to a derived name and an empty list rather than failing: a launch must not be
 * stopped by not knowing what to call a tab.
 */
public final class PrpSessions {

    private PrpSessions() { }

    static final String VERB = "prp-session";

    public static String tabName(Path prpFile) {
        if (DB.isOpen()) return quiet(() -> new PrpSessionStore().tabName(prpFile),
                TabName.of(prpFile, null));
        if (daemonRunning()) {
            var answer = ask("name", abs(prpFile));
            return answer == null || answer.isBlank() ? TabName.of(prpFile, null) : answer.strip();
        }
        return quiet(() -> new PrpSessionStore().tabName(prpFile), TabName.of(prpFile, null));
    }

    /**
     * The project's key - the {@code billing} of {@code billing-38}. Null when none is set. The name in
     * {@code prp/proj.json} wins; the index row is what projects had before that file existed.
     */
    public static String key(Path project) {
        var named = ProjFile.nameOf(project);
        return named != null ? named : dbKey(project);
    }

    static String dbKey(Path project) {
        if (DB.isOpen()) return quiet(() -> new ProjectKeyStore().of(project), null);
        if (daemonRunning()) {
            var answer = ask("key", abs(project));
            return answer == null || answer.isBlank() ? null : answer.strip();
        }
        return quiet(() -> new ProjectKeyStore().of(project), null);
    }

    public static void setKey(Path project, String key) {
        if (key == null || key.isBlank()) return;
        var clean = key.trim();
        if (clean.equals(ProjFile.nameOf(project))) return;
        ProjFile.update(project, p -> p.name(clean));
    }

    /** Keeps the index's copy of the name in step with the file, so clashes can be found. */
    static void cacheKey(Path project, String key) {
        if (key == null || key.isBlank()) return;
        if (DB.isOpen()) {
            quiet(() -> {
                new ProjectKeyStore().set(project, key);
                return null;
            }, null);
        } else if (daemonRunning()) ask("set-key", abs(project), key);
        else quiet(() -> {
            new ProjectKeyStore().set(project, key);
            return null;
        }, null);
    }

    /** Another project that already uses this name, or null. Only projects seen so far are known. */
    public static String keyOwner(String key, Path project) {
        if (DB.isOpen()) return quiet(() -> new ProjectKeyStore().ownerOf(key, project), null);
        if (daemonRunning()) {
            var answer = ask("key-owner", key, abs(project));
            return answer == null || answer.isBlank() ? null : answer.strip();
        }
        return quiet(() -> new ProjectKeyStore().ownerOf(key, project), null);
    }

    public static List<PrpSession> of(Path prpFile) {
        if (DB.isOpen()) return quiet(() -> new PrpSessionStore().of(prpFile), List.of());
        if (daemonRunning()) {
            var answer = ask("list", abs(prpFile));
            return answer == null ? List.of() : parse(answer);
        }
        return quiet(() -> new PrpSessionStore().of(prpFile), List.of());
    }

    public static PrpSession latest(Path prpFile) {
        var all = of(prpFile);
        return all.isEmpty() ? null : all.get(0);
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
        var stamp = System.currentTimeMillis();
        if (DB.isOpen()) {
            quiet(() -> {
                new PrpSessionStore().record(sessionId, prpFile, prpIndex, project, tabName, stamp, exe);
                return null;
            }, null);
            return;
        }
        if (daemonRunning()) {
            ask("record", sessionId, abs(prpFile), nz(prpIndex), abs(project), nz(tabName), nz(exe));
            return;
        }
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
     * THE LOCK IS EXCLUSIVE, so a daemon that is running is the only route there is. Opening the
     * database here as a "fallback" does not fail - it BLOCKS, which is how a launch came to hang
     * instead of falling back.
     */
    static boolean daemonRunning() {
        try {
            return Client.ping() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** The daemon's answer, or null when it did not understand. */
    static String ask(String... args) {
        try {
            var full = new ArrayList<String>(List.of("query", VERB));
            full.addAll(List.of(args));
            var reply = Client.send(full, false, m -> { });
            return reply == null || reply.exitCode != 0 ? null : reply.stdout;
        } catch (Exception e) {
            return null;
        }
    }

    /** Columns: sessionId, tabName, startedAt, prpIndex, projectPath, exe - see {@code cli/PrpSessionCmd}. */
    static List<PrpSession> parse(String tsv) {
        var out = new ArrayList<PrpSession>();
        for (var line : tsv.split("\n")) {
            var f = line.strip().split("\t", -1);
            if (f.length < 5 || f[0].isBlank()) continue;
            var row = new PrpSession();
            row.sessionId(f[0]).tabName(f[1]).prpIndex(f[3]).projectPath(f[4]);
            // a daemon from before the column existed sends five fields
            if (f.length > 5 && !f[5].isBlank()) row.exe(f[5]);
            try {
                row.startedAt(Long.parseLong(f[2]));
            } catch (NumberFormatException e) {
                row.startedAt(0L);
            }
            out.add(row);
        }
        return out;
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

    static String abs(Path p) {
        return p == null ? "" : p.toAbsolutePath().normalize().toString();
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
