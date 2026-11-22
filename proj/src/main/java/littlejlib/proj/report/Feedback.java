package littlejlib.proj.report;

import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpUuid;
import littlejlib.proj.track.Item;
import littlejlib.proj.track.ProjectScope;
import littlejlib.proj.track.Timeline;
import littlejlib.proj.track.Workplan;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;

/**
 * What the user does on the report page, written through the same code as {@code proj prp plan set/add} and
 * {@code proj prp note}: the workplan lock, a whole-state line, the history. The source says it was the user, on the web.
 */
public final class Feedback {

    private Feedback() { }

    public static final String SESSION = "web";

    static String source() {
        return "user " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")) + " (web)";
    }

    /** Applies one action to {@code Project:id}; returns a line saying what was done. */
    public static String apply(String itemRef, Map<String, String> form) throws IOException {
        var ref = ReportQuery.itemRef(itemRef);
        if (ref == null) throw new IllegalArgumentException("item is Project:id");
        var project = ProjectScope.resolve(ref[0]);
        var prpDir = project.dir().resolve("prp");
        var id = Long.parseLong(ref[1]);
        var action = form.getOrDefault("action", "");
        var text = form.getOrDefault("text", "").strip();
        var key = project.name() + "#" + id;
        return switch (action) {
            case "done", "drop", "hold", "reopen" -> {
                var status = switch (action) {
                    case "done" -> "done";
                    case "drop" -> "dropped";
                    case "hold" -> "hold";
                    default -> "open";
                };
                var next = Workplan.set(prpDir, id, i -> {
                    i.status(status).source(source());
                    if (!text.isEmpty()) i.reply(text);
                }, SESSION);
                if (!text.isEmpty()) note(prpDir, next, "note", key + " set " + status + " (web): " + text);
                yield key + " is " + status;
            }
            case "tag-critical", "tag-infra", "tag-extra" -> {
                var tag = action.substring(4);
                Workplan.set(prpDir, id, i -> i.tag(tag).source(source()), SESSION);
                yield key + " is now " + tag;
            }
            case "answer", "comment" -> {
                if (text.isEmpty()) throw new IllegalArgumentException("write the " + action + " first (a)");
                if (text.length() > Workplan.MAX_DETAIL) throw new IllegalArgumentException("at most " + Workplan.MAX_DETAIL + " characters");
                var settled = action.equals("answer");
                var next = Workplan.set(prpDir, id, i -> {
                    i.reply(text).source(source());
                    if (settled) i.status("done");
                }, SESSION);
                note(prpDir, next, settled ? "decision" : "note", (settled ? "User answered " : "User commented on ") + key + " (web): " + text);
                yield settled ? key + " answered and done" : key + ": comment recorded";
            }
            default -> throw new IllegalArgumentException("unknown action " + action);
        };
    }

    /** A new item from the page's form. */
    public static String add(Map<String, String> form) throws IOException {
        var project = ProjectScope.resolve(form.getOrDefault("project", ""));
        var prpDir = project.dir().resolve("prp");
        var draft = new Item().type(form.getOrDefault("type", "todo")).title(form.getOrDefault("title", "").strip())
                .detail(blank(form.get("detail"))).source(source()).session(SESSION).owner(null);
        var prp = blank(form.get("prp"));
        if (prp != null) draft.prp(PrpUuid.read(littlejlib.proj.cli.PrpCommands.resolve(prpDir, prp).getMainFile()));
        var after = blank(form.get("after"));
        if (after != null) {
            var ids = new ArrayList<Long>();
            for (var p : after.split("[,\\s]+")) if (!p.isBlank()) ids.add(Long.parseLong(p.strip()));
            draft.deps(ids);
        }
        var added = Workplan.add(prpDir, draft);
        return project.name() + "#" + added.id() + " added";
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** A timeline note on the item's PRP, cut to the note limit; the full words are on the item ({@code reply}). */
    static void note(Path prpDir, Item item, String kind, String text) throws IOException {
        if (item.prp() == null) return;
        for (var e : PrpCreator.list(prpDir)) {
            if (!item.prp().equals(PrpUuid.read(e.getMainFile()))) continue;
            var t = text.replaceAll("\\s*\\R\\s*", " ");
            if (t.length() > Timeline.MAX_TEXT) t = t.substring(0, Timeline.MAX_TEXT - 40) + "... (full text: plan show " + item.id() + ")";
            Timeline.add(prpDir, e.getIndex(), item.prp(), kind, t, null, SESSION);
            return;
        }
    }
}
