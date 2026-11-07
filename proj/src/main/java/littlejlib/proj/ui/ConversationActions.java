package littlejlib.proj.ui;

import javafx.application.Platform;
import littlejlib.proj.Settings;
import littlejlib.proj.hib.*;
import littlejlib.proj.prp.PrpSessions;
import littlejlib.proj.terminal.RunOutcome;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * What the Conversations tab does. Hibernating ends a running conversation, so it needs the same key
 * twice within a few seconds; restoring only opens a tab, so it does not.
 */
public final class ConversationActions {

    static final long ARM_MS = 4000;

    final Settings settings;
    final Runnable refresh;
    final Consumer<String> say;
    String armed;
    long armedAt;

    public ConversationActions(Settings settings, Runnable refresh, Consumer<String> say) {
        this.settings = settings;
        this.refresh = refresh;
        this.say = say;
    }

    public void hibernate(LiveSession s, boolean anyway) {
        if (s == null) { say.accept("select a running conversation"); return; }
        var token = (anyway ? "F7:" : "F8:") + s.pid();
        var now = System.currentTimeMillis();
        if (!token.equals(armed) || now - armedAt > ARM_MS) {
            armed = token;
            armedAt = now;
            say.accept("press " + token.substring(0, 2) + " again to hibernate " + s.label() + " and close its tab");
            return;
        }
        armed = null;
        say.accept("hibernating " + s.label() + "…");
        background(() -> {
            var o = Hibernator.run(s, false, anyway, anyway, false);
            return switch (o.exit()) {
                case 0 -> "hibernated " + s.label() + " - log " + Path.of(o.record().logFile()).getFileName();
                case Hibernator.REFUSED_NO_PRP -> s.label() + " is not on a PRP - ask its Claude to create one, or F7 twice to hibernate anyway";
                case Hibernator.REFUSED_BUSY -> s.label() + " is busy mid-turn - F7 twice to hibernate anyway";
                case Hibernator.REFUSED_UNSURE -> "cannot tell which PRP " + s.label() + " is on - choose with  proj hib tab " + s.pid() + " --prp <file>, or F7 twice for none";
                default -> String.join("; ", o.lines());
            };
        });
    }

    public void restore(HibRecord r) {
        if (r == null) { say.accept("select a hibernated conversation"); return; }
        if (!Restorer.transcriptExists(r)) {
            say.accept("transcript of " + r.label() + " is gone - F2 opens its log");
            return;
        }
        // Asked here, on the FX thread, because the answer is a dialog; a PRP session that ran on another claude is the only case that asks.
        String program;
        try {
            program = recordedExe(r);
        } catch (IllegalArgumentException e) {
            say.accept(e.getMessage());
            return;
        }
        say.accept("restoring " + r.label() + "…");
        background(() -> Restorer.restore(r, settings, null, program) == RunOutcome.FAILED
                ? "could not open a terminal - register one in Settings"
                : "restored " + r.label() + " in a new tab");
    }

    static String recordedExe(HibRecord r) {
        if (r.prpFile() == null || r.prpFile().isBlank()) return null;
        var session = PrpSessions.of(Path.of(r.prpFile())).stream()
                .filter(s -> r.sessionId() != null && r.sessionId().equals(s.sessionId())).findFirst().orElse(null);
        return ExeConfirm.programFor(session);
    }

    public void openLog(HibRecord r) {
        if (r == null || r.logFile() == null) { say.accept("select a hibernated conversation"); return; }
        PrpManagerActions.openInEditor(Path.of(r.logFile()), settings);
    }

    void background(java.util.concurrent.Callable<String> work) {
        Thread.ofVirtual().name("conversations").start(() -> {
            String msg;
            try {
                msg = work.call();
            } catch (Exception e) {
                msg = "failed: " + e.getMessage();
            }
            var shown = msg;
            Platform.runLater(() -> {
                say.accept(shown);
                refresh.run();
            });
        });
    }
}
