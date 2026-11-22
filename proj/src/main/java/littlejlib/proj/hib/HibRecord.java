package littlejlib.proj.hib;

import datapotter.datahelper.Data;

/** One hibernated conversation, as a line of {@code hibernated.jsonl}. The last line for a session id wins. */
@Data
public final class HibRecord extends HibRecord_A {

    public static final String HIBERNATED = "hibernated", RESTORED = "restored";

    String sessionId;
    String name;
    String cwd;
    String state;
    String prpFile;
    String prpIndex;
    String prpName;
    String logFile;
    String version;
    String terminal;
    String permissionMode;  // the transcript's last one, reproduced on restore
    Long hibernatedAt;
    Long restoredAt;
    Long lastActivity;
    Integer humanTurns;
    Long workingSet;
    Long privateBytes;
    String note;
    String hibernatedBy;    // "precompact" when the PreCompact hook parked it, so a resume offers continue or fresh

    public static final String BY_PRECOMPACT = "precompact";

    public boolean hibernated() {
        return HIBERNATED.equals(state);
    }

    public String label() {
        return name != null && !name.isBlank() ? name : sessionId;
    }

    public String prpLabel() {
        return prpIndex == null ? "no PRP" : "PRP " + prpIndex + (prpName == null ? "" : " " + prpName);
    }
}
