package littlejlib.proj.cct.export;

import xyz.jphil.datahelper.Data;

/**
 * One thing that happened, in the order it happened: something you typed, something Claude said, a
 * tool it ran, or what that tool answered.
 * <p>
 * The index deliberately stores only the first two of those - layer 4 is where most of the store's
 * 825 MB lives. An EXPORT is different: it reads one file straight off disk, so it can afford the
 * whole thing, and it has to, because a session log without the commands and the code is a summary
 * of the work rather than a record of it.
 */
@Data
public final class TranscriptEntry extends TranscriptEntry_A {

    String kind;
    long timestamp;
    String title;
    String body;
    String tool;
    boolean sidechain;
    boolean error;
    String model;
    String effort;
    String agentId;
    int bodyLines;
    int elidedLines;

    public static final String
        USER = "user",
        ASSISTANT = "assistant",
        THINKING = "thinking",
        TOOL_USE = "tool",
        TOOL_RESULT = "result",
        QUEUED = "queued",
        SYSTEM = "system";

    public boolean isUser() { return USER.equals(kind) || QUEUED.equals(kind); }

    public boolean isAssistant() { return ASSISTANT.equals(kind); }
}
