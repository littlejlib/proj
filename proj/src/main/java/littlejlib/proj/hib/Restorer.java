package littlejlib.proj.hib;

import module java.base;

import littlejlib.proj.Settings;
import littlejlib.proj.cct.export.SessionRead;
import littlejlib.proj.prp.PrpPrompt;
import littlejlib.proj.terminal.RunOutcome;
import littlejlib.proj.terminal.TerminalRunner;

/**
 * A new tab in the conversation's folder running {@code claude --resume <id> --name "<name>"}, in the
 * permission mode the conversation last had - never a wider one taken from settings.
 */
public final class Restorer {

    private Restorer() { }

    static final Pattern MODE = Pattern.compile("\"permissionMode\":\"([A-Za-z]+)\"");
    static final int TAIL = 1 << 20;

    public static String command(HibRecord r, Settings settings) {
        var line = new StringBuilder(PrpPrompt.program(settings)).append(" --resume ").append(r.sessionId());
        var mode = r.permissionMode() != null ? r.permissionMode() : transcript(r).map(Restorer::lastPermissionMode).orElse(null);
        if ("bypassPermissions".equals(mode)) line.append(' ').append(PrpPrompt.SKIP_PERMISSIONS);
        else if (mode != null && !mode.isBlank() && !"default".equals(mode)) line.append(" --permission-mode ").append(mode);
        if (r.name() != null && !r.name().isBlank()) line.append(" --name \"").append(r.name()).append('"');
        return line.toString();
    }

    /** claude-code prunes old transcripts; without one, --resume has nothing to resume and the log is all there is. */
    public static boolean transcriptExists(HibRecord r) {
        return transcript(r).isPresent();
    }

    static Optional<Path> transcript(HibRecord r) {
        if (r.cwd() == null) return Optional.empty();
        return SessionRead.sessionsOfDirectory(Path.of(r.cwd())).stream()
                .filter(p -> r.sessionId().equals(SessionRead.sessionIdOf(p))).findFirst();
    }

    public static RunOutcome restore(HibRecord r, Settings settings) {
        var outcome = TerminalRunner.outcomeOf(null, Path.of(r.cwd()), command(r, settings), "hib-restore",
                settings, r.name(), true);
        if (outcome != RunOutcome.FAILED) {
            r.state(HibRecord.RESTORED).restoredAt(System.currentTimeMillis());
            HibStore.append(r);
        }
        return outcome;
    }

    /** The last permission mode written into the transcript, from its final megabyte. */
    public static String lastPermissionMode(Path transcript) {
        try (var ch = FileChannel.open(transcript, StandardOpenOption.READ)) {
            var from = Math.max(0, ch.size() - TAIL);
            var buf = ByteBuffer.allocate((int) (ch.size() - from));
            ch.read(buf, from);
            var m = MODE.matcher(new String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8));
            String last = null;
            while (m.find()) last = m.group(1);
            return last;
        } catch (IOException e) {
            return null;
        }
    }
}
