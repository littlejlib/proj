package littlejlib.proj.cli;

import module java.base;

import littlejlib.proj.hib.HibRecord;
import littlejlib.proj.hib.HibStore;

/**
 * A conversation parked by the PreCompact hook was not parked by the user, so the user chooses how to come back:
 * CONTINUE re-reads the whole conversation, FRESH starts a new session from the PRP, its status file and the log tail.
 */
final class ResumeChoice {

    private ResumeChoice() { }

    static final String QUESTION = "Parked by the PreCompact hook. [C]ontinue the whole conversation, or start [F]resh from the PRP + status + log tail? ";

    /** @return the record when this session was parked by the hook and not restored since, else null */
    static HibRecord parked(String sessionId) {
        return HibStore.all().stream()
                .filter(r -> sessionId != null && sessionId.equals(r.sessionId()) && r.hibernated()
                        && HibRecord.BY_PRECOMPACT.equals(r.hibernatedBy()))
                .findFirst().orElse(null);
    }

    /** One key, no Enter on Windows ({@code choice}); a line elsewhere. Without a terminal to ask: continue. */
    static boolean askFresh() {
        var console = System.console();
        if (console == null || !console.isTerminal()) return false;
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                return new ProcessBuilder("choice", "/c", "CF", "/n", "/m", QUESTION).inheritIO().start().waitFor() == 2;
            } catch (IOException | InterruptedException e) {
                return false;
            }
        }
        var line = console.readLine(QUESTION);
        return line != null && line.strip().toLowerCase(Locale.ROOT).startsWith("f");
    }

    static void restored(HibRecord r, String how) {
        HibStore.append(r.state(HibRecord.RESTORED).restoredAt(System.currentTimeMillis()).note(how));
    }
}
