package littlejlib.proj.renumber;

import datapotter.datahelper.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The renaming of one prp folder from the legacy scheme. Empty files when the folder is already new. */
@Data
public final class RenumberPlan extends RenumberPlan_A {

    List<RenumberFile> files = new ArrayList<>();
    /** Old number to new number. */
    Map<String, String> numbers = new LinkedHashMap<>();
    /** Old main file name to the PRP's uuid, for meta.json was/wasFile. */
    Map<String, String> mains = new LinkedHashMap<>();
    List<String> notes = new ArrayList<>();
}
