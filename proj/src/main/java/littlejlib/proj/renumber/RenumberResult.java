package littlejlib.proj.renumber;

import datapotter.datahelper.Data;

import java.util.ArrayList;
import java.util.List;

/** What renumbering one prp folder did, or would do. State: new (already), refused, dry-run, done. */
@Data
public final class RenumberResult extends RenumberResult_A {

    String folder;
    String state;
    String why;
    RenumberPlan plan;
    List<RenumberReview> reviews = new ArrayList<>();
    List<String> rewritten = new ArrayList<>();
    /** References inside logs left as they are (logs are records, never reviewed). */
    Integer quiet = 0;
}
