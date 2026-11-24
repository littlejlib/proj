package littlejlib.proj.cct.model;

import datapotter.datahelper.Data;

@Data
public final class ProjectDoc extends ProjectDoc_A {

    String encoded;
    String cwd;
    String cwdKey;

    Integer sessions;
    Integer subagents;
    Long jsonlBytes;
    Long toolResultBytes;
    Long memoryBytes;
    Integer memoryFiles;
    Long lastActivity;

}
