package littlejlib.proj.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The report as data: what the json is, what the markdown and the page render. Every field an agent may filter on
 * is computed here, so a jq filter never has to know the graph.
 */
public final class ReportData {

    public String built;
    /** Minutes since the PRP data was read (0 when read for this answer). */
    public long cacheAge;
    public Scope scope = new Scope();
    public Panels panels = new Panels();
    public List<String> problems = new ArrayList<>();
    public List<Project> projects = new ArrayList<>();
    public List<Prp> prps = new ArrayList<>();
    public List<Resource> resources = new ArrayList<>();
    public List<Item> items = new ArrayList<>();

    public static final class Scope {
        public List<String> asked = new ArrayList<>();
        public List<String> followed = new ArrayList<>();
        public List<String> prps = new ArrayList<>();
        public String goal;
        public List<String> tags = new ArrayList<>();
        public boolean done;
    }

    public static final class Panels {
        /** Open items for the user, the one that unblocks most first. */
        public List<String> waiting = new ArrayList<>();
        /** Deps done, not for the user: may start, may be queued or held. */
        public List<String> ready = new ArrayList<>();
        /** Ready and free to start together, best first. */
        public List<String> startNow = new ArrayList<>();
        /** Ready but waiting for a turn on a shared resource or for a running holder. */
        public List<String> queued = new ArrayList<>();
        public List<String> doing = new ArrayList<>();
        /** The goal and everything it waits for, prerequisites first. */
        public List<String> path = new ArrayList<>();
        /** Shown items with no link to another shown item. */
        public List<String> loose = new ArrayList<>();
    }

    public static final class Project {
        public String name, uuid, dir;
        public boolean followed;
        public int items, open;
    }

    public static final class Prp {
        public String ticket, uuid, number, name, project;
        public boolean closed;
        public int open, done;
        /** The newest updated time of its items. */
        public String lastItemUpdate;
    }

    public static final class Resource {
        public String name;
        public int capacity;
        public List<String> holders, stale, queue, blocked;
    }

    public static final class Claim {
        public String session, since, lastActive, why;
        public boolean live;
    }

    public static final class PrpRef {
        public String ticket, uuid, number, name;
        public boolean closed;
    }

    public static final class Item {
        public String key, project, projectUuid;
        public long id;
        public PrpRef prp;
        public String type, status, statusSince, tag, owner, title, detail;
        public List<String> uses;
        /** Every prerequisite; {@code waitsForOpen} only those not done or dropped. */
        public List<String> waitsFor = new ArrayList<>(), waitsForOpen = new ArrayList<>(), unblocks = new ArrayList<>();
        public int unblocksOpen, score;
        public boolean ready, startNow, forUser, onGoalPath, doing, done;
        public String queuedBehind;
        public List<String> heldBy;
        public Claim claim;
        public List<String> refs;
        public String created, updated, session, source;
    }

    /** Graph columns for the page: column index to keys, top to bottom. Not in the json. */
    public transient List<List<String>> columns = new ArrayList<>();
    public transient Map<String, Item> byKey = new LinkedHashMap<>();
    public transient List<ReportBuilder.Edge> edges = new ArrayList<>();
}
