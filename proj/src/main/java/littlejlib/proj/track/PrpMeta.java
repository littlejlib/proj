package littlejlib.proj.track;

import datapotter.datahelper.Data;

import java.util.List;

/** {@code NN-prp.meta.json}: what is said ABOUT a PRP, kept out of the PRP file itself. */
@Data
public final class PrpMeta extends PrpMeta_A {

    String uuid;
    String summary;
    List<Relation> related;
    String updated;
    String was;
    String wasFile;
}
