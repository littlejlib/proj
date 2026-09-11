package littlejlib.proj.cct.index;

import datapotter.datahelper.Data;

import static littlejlib.proj.cct.Num.*;

@Data
public final class IndexReport extends IndexReport_A {

    int filesSeen;
    int unchanged;
    int tailed;
    int rebuilt;
    int failed;
    int orphansDropped;
    int projects;
    int historyRows;
    int prunedSessions;
    long bytesRead;
    long elapsedMs;

    public String summary() {
        return "%d files: %d unchanged, %d tailed, %d rebuilt, %d failed | %d projects, %d history rows, %d pruned-transcript sessions recovered | %s read in %.1fs"
            .formatted(filesSeen, unchanged, tailed, rebuilt, failed,
                projects, historyRows, prunedSessions, bytes(bytesRead), elapsedMs / 1000.0);
    }
}
