package littlejlib.proj.track;

import datapotter.datahelper.Data;

/** An item of another project: {@code {"project":"billing","uuid":"...","id":5}}. The uuid wins when the name changed. */
@Data
public final class Link extends Link_A {

    String project;
    String uuid;
    Long id;

    public String key() {
        return project + "#" + id;
    }
}
