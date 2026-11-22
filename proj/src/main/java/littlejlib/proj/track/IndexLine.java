package littlejlib.proj.track;

import datapotter.datahelper.Data;

import java.util.List;

/** One PRP in {@code prp/index.jsonl}. Everything here is derived from files; the index is only a cache. */
@Data
public final class IndexLine extends IndexLine_A {

    String uuid;
    String number;
    String name;
    String file;
    String state;
    String summary;
    List<Relation> related;
    String progress;
    String progressAt;
    Integer notes;
    Integer openItems;
    String updated;
    String was;
}
