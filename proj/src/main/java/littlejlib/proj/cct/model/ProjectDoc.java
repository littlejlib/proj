package littlejlib.proj.cct.model;

import datapotter.arcadedbhelper.*;

@ArcadeData
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

    public static final TypeDef<ProjectDoc> TYPEDEF =
        schemaBuilder()
            .factory(ProjectDoc::new)
            .unique($encoded)
            .lsmIndex($cwdKey)
            .__();
}
