package littlejlib.proj.track;

import datapotter.datahelper.Data;

import java.util.List;

/** One line of {@code prp/workplan.jsonl}: a whole snapshot of a work item; the last line of an id is its state. */
@Data
public final class Item extends Item_A {

    Long id;
    String type;
    String status;
    String title;
    String detail;
    String prp;
    List<String> related;
    List<Long> deps;
    /** Items of OTHER projects this one waits for, named by project name and uuid so a rename does not break the link. */
    List<Link> xdeps;
    List<String> refs;
    String owner;
    /** Importance: critical, infra, extra. Null: read from an old "[critical]" prefix in the detail, else infra. */
    String tag;
    /** Shared resources: items naming the same one never run together. {@code name*N} lets N run at once. */
    List<String> uses;
    /** The user's last words on it from the report page (answer or comment); earlier ones stay in the history. */
    String reply;
    String source;
    String created;
    String updated;
    String session;
}
