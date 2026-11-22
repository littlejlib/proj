package littlejlib.proj.cct.store;

import datapotter.datahelper.Data;

@Data
public final class ProjectFolder extends ProjectFolder_A {

    String encoded;
    String path;
    int sessionFiles;
    int subagentFiles;
    long jsonlBytes;
    long toolResultBytes;
    long memoryBytes;
    int memoryFiles;
    long lastModified;
}
