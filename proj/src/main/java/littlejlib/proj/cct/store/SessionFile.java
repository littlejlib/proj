package littlejlib.proj.cct.store;

import datapotter.datahelper.Data;

@Data
public final class SessionFile extends SessionFile_A {

    String path;
    String projectEncoded;
    String sessionId;
    String parentSessionId;
    boolean subagent;
    long sizeBytes;
    long lastModified;
}
