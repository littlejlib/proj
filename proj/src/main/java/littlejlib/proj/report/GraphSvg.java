package littlejlib.proj.report;

import luvx.Frag_I;

import java.util.HashMap;

import static luvml.Frags.frags;
import static littlejlib.proj.report.SvgDsl.*;

/** The layered dependency graph as SVG, laid out by {@link littlejlib.proj.track.WorkGraph#layout}. */
final class GraphSvg {

    private GraphSvg() { }

    static final int NW = 250, NH = 50, GX = 70, GY = 14, PAD = 20, TITLE_CHARS = 38;

    static Frag_I<?> render(ReportData d) {
        if (d.columns.isEmpty()) return luvml.E.div(luvml.A.class_("sub"), luvml.E.text("No links among the items shown."));
        var x = new HashMap<String, Integer>();
        var y = new HashMap<String, Integer>();
        var rowsMax = 0;
        for (int c = 0; c < d.columns.size(); c++) {
            var col = d.columns.get(c);
            rowsMax = Math.max(rowsMax, col.size());
            for (int r = 0; r < col.size(); r++) {
                x.put(col.get(r), PAD + c * (NW + GX));
                y.put(col.get(r), PAD + r * (NH + GY));
            }
        }
        var width = PAD * 2 + d.columns.size() * (NW + GX);
        var height = PAD * 2 + rowsMax * (NH + GY);
        var svg = svg(at("xmlns", "http://www.w3.org/2000/svg"), at("width", width), at("height", height), at("id", "g"));
        for (var e : d.edges) {
            if (!x.containsKey(e.from()) || !x.containsKey(e.to())) continue;
            int x1 = x.get(e.from()) + NW, y1 = y.get(e.from()) + NH / 2, x2 = x.get(e.to()), y2 = y.get(e.to()) + NH / 2, mx = (x1 + x2) / 2;
            svg.____(path(at("class", "edge" + (e.cross() ? " cross" : "")), at("data-from", e.from()), at("data-to", e.to()),
                    at("d", "M" + x1 + "," + y1 + " C" + mx + "," + y1 + " " + mx + "," + y2 + " " + x2 + "," + y2)));
        }
        for (var col : d.columns) for (var k : col) {
            var i = d.byKey.get(k);
            var crit = "critical".equals(i.tag);
            var held = "hold".equals(i.status) || "blocked".equals(i.status);
            var label = i.title.length() > TITLE_CHARS ? i.title.substring(0, TITLE_CHARS - 1) + "…" : i.title;
            var meta = i.type + (i.owner != null && !"user".equals(i.owner) ? " · " + i.owner : "") + (i.doing ? " · doing" : "") + (i.done ? " · " + i.status : "");
            if (i.uses != null && !i.uses.isEmpty()) meta += " · " + String.join(" ", i.uses.stream().map(ReportPage::chip).toList());
            svg.____(g(at("class", "node" + (i.done ? " done" : "") + (i.doing ? " doing" : "")), at("data-k", k), at("tabindex", "0"),
                    at("transform", "translate(" + x.get(k) + "," + y.get(k) + ")"),
                    rect(at("class", "box" + (crit ? " crit" : "")), at("width", NW), at("height", NH), at("rx", 8),
                            held ? at("stroke-dasharray", "5 3") : frags()),
                    rect(at("class", "bar t-" + i.type), at("width", 6), at("height", NH), at("rx", 3)),
                    text(at("x", 12), at("y", 18), at("class", "nk"), luvml.E.text(k + "  " + meta)),
                    text(at("x", 12), at("y", 37), luvml.E.text(label)),
                    title(luvml.E.text(i.title))));
        }
        return svg;
    }
}
