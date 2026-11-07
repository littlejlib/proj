package littlejlib.proj.prp;

import datapotter.datahelper.Data;

/** {@code prp/proj.json}: what a project is called and looks like. Grow it only on need. */
@Data
public final class ProjJson extends ProjJson_A {

    String uuid;      // base26, lower case; identifies the project across rename and move
    String name;      // short name, the billing of billing-38
    String longName;
    String icon;      // path (relative to the project root) or URL; overrides the repo's org icon
}
