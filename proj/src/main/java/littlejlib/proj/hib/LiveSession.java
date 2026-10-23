package littlejlib.proj.hib;

import datapotter.datahelper.Data;
import littlejlib.proj.cct.export.SessionRead;

import java.nio.file.Path;

/** A running claude-code conversation: claude-code's own registry entry joined with its process. */
@Data
public final class LiveSession extends LiveSession_A {

    Long pid;
    Long shellPid;          // the tab's shell, killed to close the tab; null when not under a terminal
    String terminal;
    String sessionId;
    String cwd;
    String name;
    String status;
    String version;
    Long startedAt;
    Long updatedAt;
    Long workingSet;
    Long privateBytes;
    Integer humanTurns;
    String prpIndex;        // filled by LiveSessions.withDetail; "-" when not on a PRP

    public long idleMinutes(long now) {
        return updatedAt == null ? -1 : Math.max(0, (now - updatedAt) / 60000);
    }

    public boolean busy() {
        return "busy".equalsIgnoreCase(status);
    }

    public Path transcript() {
        if (cwd == null || sessionId == null) return null;
        return SessionRead.sessionsOfDirectory(Path.of(cwd)).stream()
                .filter(p -> sessionId.equals(SessionRead.sessionIdOf(p))).findFirst().orElse(null);
    }

    public String label() {
        return name != null && !name.isBlank() ? name : sessionId;
    }
}
