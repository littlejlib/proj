package littlejlib.proj.cct.index;

import datapotter.datahelper.Data;

@Data
public final class IndexOptions extends IndexOptions_A {

    boolean includeAssistant = true;
    boolean includeThinking = true;
    boolean force = false;
    int batchSize = 4000;

    public static IndexOptions defaults() { return new IndexOptions(); }
}
