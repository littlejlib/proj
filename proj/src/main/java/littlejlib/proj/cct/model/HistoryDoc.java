package littlejlib.proj.cct.model;

import datapotter.arcadedbhelper.*;

@ArcadeData
public final class HistoryDoc extends HistoryDoc_A {

    String historyKey;
    String sessionId;
    String project;
    String display;
    Long timestamp;

    public static final TypeDef<HistoryDoc> TYPEDEF =
        schemaBuilder()
            .factory(HistoryDoc::new)
            .unique($historyKey)
            .lsmIndex($sessionId)
            .lsmIndex($timestamp)
            .__();
}
