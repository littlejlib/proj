package littlejlib.proj.report;

import luvml.DocType;
import luvml.o.HtmlRenderer;
import luvx.Frag_I;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static luvml.A.*;
import static luvml.E.*;
import static luvml.Frags.*;

/**
 * The report as one HTML page, built with luvml and styled with luvs ({@link ReportStyle}). Everything is drawn
 * here, the graph included; the page's script only moves the focus, opens an item and highlights its neighbours.
 * Every action has a single key and a button; writes are plain forms posted back to the daemon with the run's token.
 */
public final class ReportPage {

    private ReportPage() { }

    /** {@code token}: the daemon's per-run write token; null renders the page read-only (no forms). */
    public static String render(ReportData d, ReportQuery q, String token) {
        var self = url("/report", q.params());
        var page = frags(
                DocType.html5(),
                html(lang("en"),
                        head(
                                meta(charset("utf-8")),
                                meta(name_("viewport"), content("width=device-width, initial-scale=1")),
                                luvml.E.title("Work plan: " + projectNames(d)),
                                style(ReportStyle.CSS)),
                        body(
                                header(d, q, self),
                                div(class_("grid"),
                                        panel("Waiting on you", "w", "Answers and decisions, the one that unblocks most first.", d.panels.waiting, d),
                                        startPanel(d),
                                        d.scope.goal == null
                                                ? panel("Doing", "g", "Items an agent or person has started.", d.panels.doing, d)
                                                : panel("Path to " + d.scope.goal, "p", "Everything the goal waits for, prerequisites first.", d.panels.path, d)),
                                d.scope.goal != null && !d.panels.doing.isEmpty()
                                        ? div(class_("grid"), panel("Doing", "g", "Items an agent or person has started.", d.panels.doing, d)) : frags(),
                                resources(d),
                                problems(d),
                                section(id("graph"), class_("card wide"), h2(text("Graph")),
                                        div(class_("sub"), text("Columns run from what comes first to what waits. Dashed lines cross projects; a red border is critical; chips are shared resources. Enter on an item highlights what it waits for and what it unblocks.")),
                                        div(class_("graphwrap"), GraphSvg.render(d))),
                                loose(d),
                                itemDetails(d, token, self),
                                newItemForm(d, token, self),
                                help(),
                                script(ReportScript.JS))));
        return HtmlRenderer.asString(page);
    }

    static String projectNames(ReportData d) {
        return String.join(", ", d.projects.stream().map(p -> p.name).toList());
    }

    static Frag_I<?> header(ReportData d, ReportQuery q, String self) {
        var open = d.items.stream().filter(i -> !i.done).count();
        var links = frags(
                a(href(url("/report", q.withDone(!q.done()).params())), text(q.done() ? "hide done" : "show done")), text(" · "),
                a(href(url("/report.md", q.params())), text("markdown")), text(" · "),
                a(href(url("/report.json", q.params())), text("json")), text(" · "),
                a(href("#help"), text("keys (?)")));
        return luvml.E.header(
                h1(text("Work plan: " + projectNames(d))),
                div(class_("sub"), text("Built " + littlejlib.proj.track.Now.shown(d.built) + " · " + d.items.size() + " items shown, " + open
                        + " not done · " + d.prps.size() + " PRPs" + (d.scope.followed.isEmpty() ? "" : " · followed: " + String.join(", ", d.scope.followed))
                        + (d.cacheAge > 0 ? " · PRP data " + d.cacheAge + " min old" : ""))),
                form(id("scope"), action("/report"), method("get"), class_("scope"),
                        label(text("projects "), input(typeText(), name_("with"), id("f-with"), value(String.join(",", q.with())), size("28"), placeholder("name, alias, uuid or path"))),
                        label(text(" goal "), input(typeText(), name_("goal"), value(q.goal() == null ? "" : q.goal().replace('#', ':')), size("12"), placeholder("Project:id"))),
                        label(text(" PRPs "), input(typeText(), name_("prp"), value(String.join(",", q.prps())), size("16"), placeholder("ticket or uuid"))),
                        q.done() ? input(typeHidden(), name_("done"), value("1")) : frags(),
                        button(typeSubmit(), text("Show"))),
                nav(class_("sub"), links));
    }

    static Frag_I<?> panel(String title, String key, String note, List<String> keys, ReportData d) {
        var rows = frags();
        for (var k : keys) rows.____(row(d.byKey.get(k), k));
        if (keys.isEmpty()) rows.____(li(class_("none"), text("Nothing.")));
        return section(class_("card"), data("panel", key),
                h2(text(title + " (" + keys.size() + ")")), div(class_("sub"), text(note)), ul(class_("list"), rows));
    }

    static Frag_I<?> startPanel(ReportData d) {
        var rows = frags();
        for (var k : d.panels.startNow) rows.____(row(d.byKey.get(k), k));
        if (d.panels.startNow.isEmpty()) rows.____(li(class_("none"), text("Nothing.")));
        var queued = frags();
        for (var k : d.panels.queued) {
            var i = d.byKey.get(k);
            var why = i.heldBy != null ? "held by " + String.join(", ", i.heldBy) : i.queuedBehind != null ? "after " + i.queuedBehind : "";
            queued.____(row(i, k, why));
        }
        return section(class_("card"), data("panel", "s"),
                h2(text("Start now (" + d.panels.startNow.size() + ")")),
                div(class_("sub"), text("Prerequisites done and no shared resource in the way; these can run together. Best first.")),
                ul(class_("list"), rows),
                d.panels.queued.isEmpty() ? frags() : frags(h3(text("Queued for a shared resource (" + d.panels.queued.size() + ")")), ul(class_("list"), queued)));
    }

    static Frag_I<?> row(ReportData.Item i, String k) {
        return row(i, k, null);
    }

    static Frag_I<?> row(ReportData.Item i, String k, String extra) {
        if (i == null) return li(class_("none"), text(k + " (not shown)"));
        var sub = new ArrayList<String>();
        if (i.prp != null) sub.add(i.prp.ticket != null ? i.prp.ticket + " " + nice(i.prp.name) : "PRP " + i.prp.uuid);
        if (!"open".equals(i.status)) sub.add(i.status + since(i));
        if (i.owner != null) sub.add("owner " + i.owner);
        if (!i.waitsForOpen.isEmpty()) sub.add("waits for " + String.join(", ", i.waitsForOpen));
        if (extra != null && !extra.isEmpty()) sub.add(extra);
        if (i.claim != null && !i.claim.live) sub.add("STALE claim: " + i.claim.why);
        return li(data("k", i.key), tabindex("0"), class_("row t-" + i.type + (i.done ? " done" : "")),
                span(class_("k"), text(i.key)), span(class_("ty t-" + i.type), text(i.type)), text(" "), text(i.title), badges(i),
                div(class_("sub"), text(String.join(" · ", sub))));
    }

    static String since(ReportData.Item i) {
        return i.statusSince == null ? "" : " since " + littlejlib.proj.track.Now.shown(i.statusSince);
    }

    static String nice(String prpName) {
        return prpName == null ? "" : prpName.replace('_', ' ').replace('-', ' ');
    }

    static Frag_I<?> badges(ReportData.Item i) {
        var b = frags(span(class_("badge b-" + i.tag), text(i.tag)));
        if (i.owner != null && !"user".equals(i.owner)) b.____(span(class_("badge b-owner"), text(i.owner)));
        if (i.unblocksOpen > 0) b.____(span(class_("badge b-n"), text("unblocks " + i.unblocksOpen)));
        if (i.uses != null) for (var u : i.uses) b.____(span(class_("chip"), titleAttr(u), text(chip(u))));
        return b;
    }

    /** {@code git:billing-core*2} shows as {@code billing-core}. */
    static String chip(String use) {
        var s = use.replaceAll("\\*\\d+$", "");
        var colon = s.indexOf(':');
        return colon < 0 ? s : s.substring(colon + 1);
    }

    static Frag_I<?> resources(ReportData d) {
        if (d.resources.isEmpty()) return frags();
        var rows = frags();
        for (var r : d.resources) {
            var parts = new ArrayList<String>();
            parts.add(r.holders.isEmpty() ? "free" : "held by " + String.join(", ", r.holders));
            if (!r.stale.isEmpty()) parts.add("stale claims " + String.join(", ", r.stale));
            if (!r.queue.isEmpty()) parts.add("queue " + String.join(", ", r.queue));
            if (!r.blocked.isEmpty()) parts.add("waiting for prerequisites " + String.join(", ", r.blocked));
            rows.____(li(span(class_("chip"), text(r.name + (r.capacity > 1 ? " ×" + r.capacity : ""))), text(" " + String.join(" · ", parts))));
        }
        return section(class_("card wide"), h2(text("Shared resources (" + d.resources.size() + ")")),
                div(class_("sub"), text("Items naming the same resource never run together; ×N lets N run at once.")), ul(class_("plain"), rows));
    }

    static Frag_I<?> problems(ReportData d) {
        if (d.problems.isEmpty()) return frags();
        var rows = frags();
        for (var p : d.problems) rows.____(li(text(p)));
        return section(class_("card wide problems"), h2(text("Problems (" + d.problems.size() + ")")), ul(class_("plain"), rows));
    }

    static Frag_I<?> loose(ReportData d) {
        var byPrp = new LinkedHashMap<String, List<String>>();
        for (var k : d.panels.loose) {
            var i = d.byKey.get(k);
            var label = i.prp != null && i.prp.ticket != null ? i.prp.ticket + " " + nice(i.prp.name) : i.project + " (no PRP)";
            byPrp.computeIfAbsent(label, x -> new ArrayList<>()).add(k);
        }
        var groups = frags();
        for (var e : byPrp.entrySet()) {
            var rows = frags();
            e.getValue().stream().sorted((a, b) -> d.byKey.get(b).score - d.byKey.get(a).score).forEach(k -> rows.____(row(d.byKey.get(k), k)));
            groups.____(details(attr("open", "open"), summary(text(e.getKey() + " (" + e.getValue().size() + ")")), ul(class_("list"), rows)));
        }
        return section(class_("card wide"), data("panel", "l"), h2(text("Not linked to anything shown (" + d.panels.loose.size() + ")")), groups);
    }

    /** One hidden section per item; the script shows the one asked for in the side panel. */
    static Frag_I<?> itemDetails(ReportData d, String token, String self) {
        var all = frags();
        for (var i : d.items) {
            var rel = frags();
            rel.____(h3(text("Waits for")), refs(d, i.waitsFor), h3(text("Unblocks")), refs(d, i.unblocks));
            var facts = new ArrayList<String>();
            facts.add(i.type + " · " + i.status + since(i));
            facts.add("importance " + i.tag);
            if (i.owner != null) facts.add("owner " + i.owner);
            if (i.uses != null) facts.add("uses " + String.join(", ", i.uses));
            if (i.claim != null) facts.add((i.claim.live ? "claim live" : "STALE claim") + " (" + i.claim.why + ")");
            all.____(section(id("d-" + domId(i.key)), class_("item-detail"), hidden(),
                    div(class_("k"), text(i.key + (i.prp != null && i.prp.ticket != null ? " · " + i.prp.ticket + " " + nice(i.prp.name) : ""))),
                    h2(text(i.title)),
                    div(class_("sub"), text(String.join(" · ", facts))),
                    i.detail == null ? frags() : p(class_("detail"), text(i.detail)),
                    div(class_("sub"), text("source " + (i.source == null ? "-" : i.source) + " · updated " + littlejlib.proj.track.Now.shown(i.updated)
                            + (i.session == null ? "" : " · session " + i.session.substring(0, Math.min(8, i.session.length()))))),
                    token == null ? frags() : actions(i, token, self),
                    rel));
        }
        return div(id("details"), all);
    }

    static Frag_I<?> refs(ReportData d, List<String> keys) {
        if (keys.isEmpty()) return div(class_("sub"), text("none"));
        var out = ul(class_("plain"));
        for (var k : keys) {
            var i = d.byKey.get(k);
            out.____(li(data("k", k), tabindex("0"), class_("ref"), span(class_("k"), text(k)),
                    text(i == null ? "(not shown)" : i.title + " (" + i.status + ")")));
        }
        return out;
    }

    /** The feedback controls: each a plain post form; the script gives each button its single key. */
    static Frag_I<?> actions(ReportData.Item i, String token, String self) {
        var post = "/item/" + i.key.replace('#', ':');
        var forUser = i.forUser && !i.done;
        return div(class_("actions"),
                forUser ? form(action(post), method("post"), class_("answer"),
                        hiddenFields(token, self),
                        label(text("Your answer (a). Then Esc, and y to record it as settled or c to keep the item open.")),
                        textarea(name_("text"), rows("3"), data("key", "a"), placeholder("Plain words. It goes into the PRP as a decision, with today's time.")),
                        button(typeSubmit(), name_("action"), value("answer"), data("key", "y"), text("Settled: record and mark done (y)")),
                        button(typeSubmit(), name_("action"), value("comment"), data("key", "c"), text("Comment, keep open (c)")))
                        : form(action(post), method("post"), class_("answer"),
                        hiddenFields(token, self),
                        textarea(name_("text"), rows("2"), data("key", "a"), placeholder("A comment (a), written into the PRP")),
                        button(typeSubmit(), name_("action"), value("comment"), data("key", "c"), text("Comment (c)"))),
                form(action(post), method("post"), class_("buttons"),
                        hiddenFields(token, self),
                        actionButton("done", "d", "Done (d)"),
                        actionButton("drop", "x", "Drop (x)"),
                        actionButton("hold", "h", "Hold (h)"),
                        actionButton("reopen", "o", "Reopen (o)"),
                        actionButton("tag-critical", "1", "critical (1)"),
                        actionButton("tag-infra", "2", "infra (2)"),
                        actionButton("tag-extra", "3", "extra (3)")));
    }

    static Frag_I<?> actionButton(String action, String key, String label) {
        return button(typeSubmit(), name_("action"), value(action), data("key", key), text(label));
    }

    static Frag_I<?> hiddenFields(String token, String self) {
        return frags(input(typeHidden(), name_("token"), value(token)), input(typeHidden(), name_("back"), value(self)));
    }

    static Frag_I<?> newItemForm(ReportData d, String token, String self) {
        if (token == null) return frags();
        var projects = frags();
        for (var p : d.projects) projects.____(option(value(p.name), text(p.name)));
        return section(id("new"), class_("card wide"), hidden(),
                h2(text("New item (n)")),
                form(action("/item"), method("post"), class_("newitem"),
                        hiddenFields(token, self),
                        label(text("Title "), input(typeText(), name_("title"), id("new-title"), size("60"), attr("maxlength", "100"))),
                        label(text(" Project "), select(name_("project"), projects)),
                        label(text(" Type "), select(name_("type"), option(value("todo"), text("todo")), option(value("discuss"), text("discuss")), option(value("confirm"), text("confirm")))),
                        label(text(" PRP "), input(typeText(), name_("prp"), size("10"), placeholder("number"))),
                        label(text(" waits for "), input(typeText(), name_("after"), size("14"), placeholder("ids, e.g. 3,7"))),
                        label(text(" Detail "), textarea(name_("detail"), rows("2"), attr("maxlength", "1000"))),
                        button(typeSubmit(), text("Add"))));
    }

    static Frag_I<?> help() {
        String[][] keys = {
                {"j / k", "next / previous item"}, {"Enter", "open the item: details, links, actions; highlight in the graph"},
                {"Esc", "close the item, leave a text box"}, {"w s p g l", "jump to: waiting, start now, path, doing, not linked"},
                {"a", "write an answer or comment for the open item"}, {"y", "send it as settled (marks done, recorded as a decision)"},
                {"c", "send it as a comment, item stays open"}, {"d x h o", "done, drop, hold, reopen the open item"},
                {"1 2 3", "importance critical, infra, extra"}, {"n", "new item"}, {"/", "change the projects shown"},
                {"r", "reload (the page is computed again from the files)"}, {"?", "this list"}};
        var rows = frags();
        for (var k : keys) rows.____(li(span(class_("kbd"), text(k[0])), text(" " + k[1])));
        return section(id("help"), class_("card wide"), h2(text("Keys")),
                div(class_("sub"), text("Single keys, no Ctrl, Alt or Shift. They do nothing while you are typing in a box; Esc leaves the box.")),
                ul(class_("plain"), rows));
    }

    /** "billing#31" as an id-safe word. */
    public static String domId(String key) {
        return key.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    public static String url(String path, Map<String, String> params) {
        if (params.isEmpty()) return path;
        var sb = new StringBuilder(path).append('?');
        var first = true;
        for (var e : params.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8).replace("%2C", ",").replace("%3A", ":"));
        }
        return sb.toString();
    }
}
