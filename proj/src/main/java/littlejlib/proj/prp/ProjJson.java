package littlejlib.proj.prp;

import datapotter.datahelper.Data;

import java.util.List;

/** {@code prp/proj.json}: what a project is called and looks like. Grow it only on need. */
@Data
public final class ProjJson extends ProjJson_A {

    String uuid;      // base26, lower case; identifies the project across rename and move
    String name;      // short name, the billing of billing-38
    String alias3;    // exactly 3 letters or digits; accepted wherever the short name is
    String alias4;    // exactly 4
    String longName;
    String icon;      // path (relative to the project root) or URL; overrides the repo's org icon
    List<String> prpGuide;  // guide sources after the global ones, e.g. ["extend:git@host:org/repo.git#main:guide.md"]; see GuideSource. An old plain string is read as one replace entry
}
