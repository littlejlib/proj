package littlejlib.proj.track;

import datapotter.datahelper.Data;

import java.util.List;

/** One line of a PRP's timeline, {@code NN-prp.status.jsonl}. */
@Data
public final class Note extends Note_A {

    String at;
    String prp;
    String session;
    String kind;
    String text;
    List<String> refs;
}
