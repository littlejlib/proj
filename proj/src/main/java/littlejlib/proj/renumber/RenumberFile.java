package littlejlib.proj.renumber;

import datapotter.datahelper.Data;

/** One line of {@code prp/renumber-map.jsonl}: a file renamed from the legacy scheme. */
@Data
public final class RenumberFile extends RenumberFile_A {

    String oldFile;
    String newFile;
    String oldNumber;
    String newNumber;
    String kind;
    String uuid;
}
