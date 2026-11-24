package littlejlib.proj.cct.model;

import datapotter.datahelper.Data;

@Data
public final class SessionDoc extends SessionDoc_A {

    String sessionId;
    String filePath;
    String projectEncoded;
    String cwd;
    String aiTitle;
    String firstHumanPrompt;
    String lastPrompt;

    Long startedAt;
    Long lastActivity;

    Integer recordCount;
    Long fileBytes;
    Long imageLineBytes;

    Integer humanTurns;
    Integer assistantTurns;
    Integer thinkingTurns;
    Integer toolResultBlocks;

    Long inputTokens;
    Long outputTokens;
    Long cacheReadTokens;
    Long cacheCreateTokens;

    String models;
    String efforts;
    String gitBranch;
    String permissionModes;
    String sessionKind;
    String entrypoint;
    String version;

    Boolean subagent;
    Boolean transcriptMissing;
    String parentSessionId;
    Integer compactions;

    Long indexedOffset;
    Long indexedSize;
    Long indexedMtime;

}
