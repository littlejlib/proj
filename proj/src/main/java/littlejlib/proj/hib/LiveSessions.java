package littlejlib.proj.hib;

import module java.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import littlejlib.proj.cct.export.SessionRead;

/**
 * The claude-code conversations running now, from claude-code's registry {@code ~/.claude/sessions/<pid>.json}.
 * A registry file outlives its process, so only entries whose pid is a live claude are kept.
 */
public final class LiveSessions {

    private LiveSessions() { }

    static final Path REGISTRY = Path.of(System.getProperty("user.home"), ".claude", "sessions");
    static final Set<String>
            SHELLS = Set.of("cmd.exe", "powershell.exe", "pwsh.exe", "bash.exe", "sh.exe"),
            TERMINALS = Set.of("tabby.exe", "windowsterminal.exe", "openconsole.exe", "conhost.exe",
                    "wezterm-gui.exe", "alacritty.exe");
    static final ObjectMapper JSON = new ObjectMapper();

    public static List<LiveSession> all() {
        var out = new ArrayList<LiveSession>();
        try (var files = Files.list(REGISTRY)) {
            files.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .map(LiveSessions::read).filter(Objects::nonNull).forEach(out::add);
        } catch (IOException e) {
            return out;
        }
        out.sort(Comparator.comparingLong((LiveSession s) -> s.updatedAt() == null ? 0 : s.updatedAt()));
        return out;
    }

    static LiveSession read(Path f) {
        try {
            var n = JSON.readTree(f.toFile());
            var pid = n.path("pid").asLong(0);
            var proc = ProcessHandle.of(pid).orElse(null);
            if (proc == null || !proc.isAlive() || !sameProcess(proc, n.path("procStart").asText(""))) return null;
            var s = new LiveSession().pid(pid).sessionId(text(n, "sessionId")).cwd(text(n, "cwd"))
                    .name(text(n, "name")).status(text(n, "status")).version(text(n, "version"))
                    .startedAt(n.path("startedAt").asLong(0)).updatedAt(n.path("updatedAt").asLong(0));
            proc.parent().ifPresent(sh -> sh.parent().ifPresent(term -> {
                if (SHELLS.contains(exe(sh).toLowerCase(Locale.ROOT))
                        && TERMINALS.contains(exe(term).toLowerCase(Locale.ROOT)))
                    s.shellPid(sh.pid()).terminal(exe(term));
            }));
            var mem = ProcMem.of(pid);
            if (mem != null) s.workingSet(mem.workingSet()).privateBytes(mem.privateBytes());
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** The conversation this process was started from, e.g. by {@code !proj hib}: the nearest claude ancestor. */
    public static LiveSession self() {
        var running = all();
        for (var p = ProcessHandle.current().parent().orElse(null); p != null; p = p.parent().orElse(null)) {
            var pid = p.pid();
            var hit = running.stream().filter(s -> s.pid() == pid).findFirst();
            if (hit.isPresent()) return hit.get();
        }
        return null;
    }

    /** By pid, exact name, session id prefix, then name fragment. */
    public static List<LiveSession> find(String target) {
        var all = all();
        var t = target.strip().toLowerCase(Locale.ROOT);
        if (t.matches("\\d+")) {
            var pid = Long.parseLong(t);
            var byPid = all.stream().filter(s -> s.pid() == pid).toList();
            if (!byPid.isEmpty()) return byPid;
        }
        for (var test : List.<java.util.function.Predicate<LiveSession>>of(
                s -> t.equals(lower(s.name())),
                s -> lower(s.sessionId()).startsWith(t),
                s -> lower(s.name()).contains(t))) {
            var hits = all.stream().filter(test).toList();
            if (!hits.isEmpty()) return hits;
        }
        return List.of();
    }

    /** Reads each transcript for its human-turn count and PRP. A pass over every file, so it is opt-in. */
    public static List<LiveSession> withDetail(List<LiveSession> sessions) {
        sessions.parallelStream().forEach(s -> {
            var file = s.transcript();
            if (file == null) return;
            try {
                s.humanTurns(SessionRead.of(file, 1).session().humanTurns());
                var pick = SessionPrp.pick(s, file);
                s.prpIndex(pick == null ? "-" : pick.sure() ? pick.entry().getIndex() : "?");
                if (pick != null) s.prpFile(pick.entry().getMainFile().toString());
            } catch (RuntimeException ignored) { }
        });
        return sessions;
    }

    /**
     * A registry file outlives its process and pids are reused, so the pid must still be the process
     * that wrote it. procStart is its creation time as a FILETIME; the image name is no test, because
     * claude-code updates its exe in place under conversations that are still running.
     */
    static boolean sameProcess(ProcessHandle p, String procStart) {
        var started = p.info().startInstant().orElse(null);
        if (started != null && procStart.matches("\\d+")) {
            var ms = Long.parseLong(procStart) / 10_000 - 11_644_473_600_000L;
            return Math.abs(started.toEpochMilli() - ms) < 2000;
        }
        return exe(p).toLowerCase(Locale.ROOT).startsWith("claude");
    }

    static String exe(ProcessHandle p) {
        return p.info().command().map(c -> Path.of(c).getFileName().toString()).orElse("");
    }

    static String text(com.fasterxml.jackson.databind.JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
