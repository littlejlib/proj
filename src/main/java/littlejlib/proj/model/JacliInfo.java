package littlejlib.proj.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * jacli's view of one project, read through the jacli daemon rather than its ArcadeDB index -
 * embedded ArcadeDB holds an exclusive lock on that directory and the jacli daemon owns it.
 * Populated in phase D; a null JacliInfo on a node means "not asked yet", which is a different
 * thing from {@link JacliState#UNINDEXED}, meaning "asked, and jacli does not know it".
 */
@Getter
@Setter
@Accessors(chain = true)
@ToString
public class JacliInfo {

    JacliState state = JacliState.UNINDEXED;
    String coords = "";
    String tier = "";
    String classpathSource = "";
    String notes = "";
    int files;
    int declCount;
    int refCount;
    Long indexedAt;

    public String summary() {
        if (!state.isKnown()) return "";
        return tier + "  " + files + " files, " + declCount + " decls, " + refCount + " refs";
    }
}
