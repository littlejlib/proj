package littlejlib.proj.migrate;

import datapotter.datahelper.Data;

/** One fxterm-tabs tab as {@code fxterm-tabs ctl list} shows it. */
@Data
public final class TabInfo extends TabInfo_A {

    String id;
    String title;
    String folder;
    Boolean live;
    String session;     // the claude session of a live tab
}
