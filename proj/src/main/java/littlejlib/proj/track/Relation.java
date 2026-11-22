package littlejlib.proj.track;

import datapotter.datahelper.Data;

/** A PRP's relation to another PRP, by the other PRP's uuid. {@code derived}: the reverse of a relation written by the other PRP (index only). */
@Data
public final class Relation extends Relation_A {

    String prp;
    String kind;
    String note;
    Boolean derived;
}
