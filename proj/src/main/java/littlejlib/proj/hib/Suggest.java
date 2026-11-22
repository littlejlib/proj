package littlejlib.proj.hib;

import module java.base;

/**
 * Which running conversations are worth hibernating: idle ones (not busy, not waiting on a
 * permission prompt, not running a shell command), idle at least a threshold, longest idle first.
 * Never the conversation that asked.
 */
public final class Suggest {

    private Suggest() { }

    public record Candidate(LiveSession session, boolean onPrp, String reason) { }

    public static List<Candidate> of(List<LiveSession> running, Long selfPid, long minIdleMinutes) {
        var now = System.currentTimeMillis();
        return running.stream()
                .filter(s -> !s.pid().equals(selfPid))
                .filter(s -> "idle".equalsIgnoreCase(s.status()))
                .filter(s -> s.idleMinutes(now) >= minIdleMinutes)
                .sorted(Comparator.comparingLong((LiveSession s) -> s.idleMinutes(now)).reversed()
                        .thenComparing(Comparator.comparingLong((LiveSession s) -> nz(s.workingSet())).reversed()))
                .map(s -> new Candidate(s, s.prpIndex() != null && !"-".equals(s.prpIndex()) && !"?".equals(s.prpIndex()), reason(s, now)))
                .toList();
    }

    static String reason(LiveSession s, long now) {
        var turns = s.humanTurns() == null ? "" : ", " + s.humanTurns() + " turns";
        return "idle " + duration(s.idleMinutes(now)) + turns + ", " + ProcMem.mb(s.workingSet()) + " MB RAM, "
                + ProcMem.mb(s.privateBytes()) + " MB committed";
    }

    public static String duration(long minutes) {
        if (minutes < 60) return minutes + " min";
        if (minutes < 48 * 60) return (minutes / 60) + " h";
        return (minutes / 1440) + " days";
    }

    public static long ram(List<Candidate> cs) {
        return cs.stream().mapToLong(c -> nz(c.session().workingSet())).sum();
    }

    public static long committed(List<Candidate> cs) {
        return cs.stream().mapToLong(c -> nz(c.session().privateBytes())).sum();
    }

    static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
