package littlejlib.proj.precompact;

import datapotter.datahelper.Data;

/** One tab of {@code fxterm-tabs ctl list}, with the claude conversation running in it. */
@Data
public final class TabInfo extends TabInfo_A {

    String id;
    String title;
    String folder;
    Boolean live;
    String claudeSession;
    String claudeStatus;
    String claudeName;

    public boolean idle() {
        return "idle".equalsIgnoreCase(claudeStatus);
    }

    public boolean busy() {
        return "busy".equalsIgnoreCase(claudeStatus);
    }
}
