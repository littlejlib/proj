package littlejlib.proj.cct.store;

import datapotter.datahelper.Data;

@Data
public final class RawRecord extends RawRecord_A {

    String type;
    String uuid;
    String parentUuid;
    String timestampIso;
    long timestampMillis;

    String cwd;
    String sessionId;
    String version;
    String gitBranch;
    String userType;
    String entrypoint;
    String sessionKind;
    String permissionMode;

    String promptSource;
    String originKind;
    boolean sidechain;
    String agentId;

    String role;
    String model;
    String effort;
    long inputTokens;
    long outputTokens;
    long cacheReadTokens;
    long cacheCreateTokens;

    String text;
    String thinking;
    int toolResultBlocks;
    boolean image;
    boolean toolUse;

    String aiTitle;
    String lastPrompt;
    String agentName;

    String operation;
    String content;

    String systemSubtype;
    int messageCount;

    String display;
    String project;

    int lineBytes;
}
