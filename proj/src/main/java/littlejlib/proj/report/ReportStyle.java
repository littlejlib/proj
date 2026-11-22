package littlejlib.proj.report;

import luvs.CssProperty;
import luvs.CssPropertyFrag;
import luvs.CssRules;
import luvs.CssVariable;

import java.util.Arrays;

import static luvs.CssRules.rulesFrom;
import static luvs.HtmlTag.$root;
import static luvs.MQ.media;
import static luvs.MQ.prefersColorScheme;
import static luvs.Selector.selector;

/** The page's stylesheet in luvs: colour tokens on :root, redefined for dark mode, then the rules. */
final class ReportStyle {

    private ReportStyle() { }

    enum Tok implements CssVariable {
        bg("#f7f5f0", "#16150f"), panel("#fff", "#201e18"), ink("#1f1d1a", "#ece8df"), muted("#6b665d", "#a39d90"),
        line("#d9d3c7", "#3a362d"), todo("#2f6fb2", "#6aa5e6"), confirm("#c27c0e", "#e3a63a"), discuss("#7a4bb5", "#b18be6"),
        crit("#b3261e", "#ef6b62"), infra("#2e7d5b", "#5fc199"), extra("#8a8478", "#9d978b"), done("#9aa39a", "#5d655d"),
        hl("#f2c94c", "#8a6d10"), chipbg("#e8e2d4", "#3a362d");

        final String light, dark;

        Tok(String light, String dark) {
            this.light = light;
            this.dark = dark;
        }
    }

    static CssPropertyFrag p(String name, Object value) {
        return new CssProperty(name, String.valueOf(value));
    }

    static CssPropertyFrag[] defs(boolean dark) {
        return Arrays.stream(Tok.values()).map(t -> t.def(dark ? t.dark : t.light)).toArray(CssPropertyFrag[]::new);
    }

    static String v(Tok t) {
        return t.ref();
    }

    static final String CSS = build().toString();

    static CssRules build() {
        return rulesFrom(
                $root.____(defs(false)),
                media(prefersColorScheme("dark"), selector(":root").____(defs(true))),
                selector("*").____(p("box-sizing", "border-box")),
                selector("body").____(p("margin", 0), p("background", v(Tok.bg)), p("color", v(Tok.ink)), p("font", "14px/1.45 system-ui, 'Segoe UI', sans-serif")),
                selector("header").____(p("padding", "16px 20px 6px")),
                selector("h1").____(p("margin", 0), p("font-size", "22px")),
                selector("h2").____(p("font-size", "16px"), p("margin", "0 0 6px")),
                selector("h3").____(p("font-size", "14px"), p("margin", "10px 0 4px")),
                selector("a").____(p("color", v(Tok.todo))),
                selector(".sub").____(p("color", v(Tok.muted)), p("font-size", "13px")),
                selector(".scope").____(p("margin", "8px 0 4px"), p("display", "flex"), p("flex-wrap", "wrap"), p("gap", "6px"), p("align-items", "center")),
                selector("input, select, textarea, button").____(p("font", "inherit"), p("color", v(Tok.ink)), p("background", v(Tok.panel)),
                        p("border", "1px solid " + v(Tok.line)), p("border-radius", "6px"), p("padding", "3px 6px")),
                selector("button").____(p("cursor", "pointer")),
                selector("button:focus, input:focus, textarea:focus, .row:focus, .ref:focus, .node:focus").____(p("outline", "3px solid " + v(Tok.hl)), p("outline-offset", "1px")),
                selector(".grid").____(p("display", "grid"), p("grid-template-columns", "repeat(auto-fit, minmax(340px, 1fr))"), p("gap", "14px"), p("padding", "10px 20px")),
                selector(".card").____(p("background", v(Tok.panel)), p("border", "1px solid " + v(Tok.line)), p("border-radius", "10px"), p("padding", "14px"), p("min-width", 0)),
                selector(".card.wide").____(p("margin", "10px 20px")),
                selector(".list").____(p("list-style", "none"), p("margin", 0), p("padding", 0), p("max-height", "440px"), p("overflow", "auto")),
                selector(".plain").____(p("margin", "4px 0"), p("padding-left", "18px")),
                selector(".list li").____(p("padding", "7px 4px"), p("border-bottom", "1px solid " + v(Tok.line)), p("cursor", "pointer")),
                selector(".list li.current, .list li:hover").____(p("background", v(Tok.hl))),
                selector(".row.done").____(p("opacity", ".6")),
                selector(".k").____(p("font", "600 12px ui-monospace, Consolas, monospace"), p("color", v(Tok.muted)), p("margin-right", "6px")),
                selector(".ty").____(p("font-weight", 600), p("margin-right", "2px")),
                selector(".t-todo").____(p("color", v(Tok.todo))),
                selector(".t-confirm").____(p("color", v(Tok.confirm))),
                selector(".t-discuss").____(p("color", v(Tok.discuss))),
                selector(".badge").____(p("display", "inline-block"), p("font-size", "11px"), p("padding", "0 6px"), p("border-radius", "9px"), p("margin-left", "4px"), p("color", "#fff")),
                selector(".b-critical").____(p("background", v(Tok.crit))),
                selector(".b-infra").____(p("background", v(Tok.infra))),
                selector(".b-extra").____(p("background", v(Tok.extra))),
                selector(".b-owner").____(p("background", "#555")),
                selector(".b-n").____(p("background", v(Tok.todo))),
                selector(".chip").____(p("display", "inline-block"), p("font", "11px ui-monospace, Consolas, monospace"), p("padding", "0 6px"),
                        p("border-radius", "4px"), p("margin-left", "4px"), p("background", v(Tok.chipbg))),
                selector(".problems li").____(p("color", v(Tok.crit))),
                selector(".graphwrap").____(p("overflow", "auto"), p("max-height", "80vh")),
                selector("svg text").____(p("fill", v(Tok.ink)), p("font", "12px system-ui, 'Segoe UI', sans-serif")),
                selector("svg text.nk").____(p("fill", v(Tok.muted)), p("font-size", "11px")),
                selector(".box").____(p("fill", v(Tok.panel)), p("stroke", v(Tok.line)), p("stroke-width", "1.5")),
                selector(".box.crit").____(p("stroke", v(Tok.crit)), p("stroke-width", "2.5")),
                selector(".bar.t-todo").____(p("fill", v(Tok.todo))),
                selector(".bar.t-confirm").____(p("fill", v(Tok.confirm))),
                selector(".bar.t-discuss").____(p("fill", v(Tok.discuss))),
                selector(".node").____(p("cursor", "pointer")),
                selector(".node.done").____(p("opacity", ".5")),
                selector(".node.doing .box").____(p("stroke", v(Tok.todo)), p("stroke-width", "3")),
                selector(".node.dim, .edge.dim").____(p("opacity", ".12")),
                selector(".node.hot .box").____(p("fill", v(Tok.hl))),
                selector(".edge").____(p("fill", "none"), p("stroke", v(Tok.muted)), p("stroke-width", "1.2"), p("opacity", ".55")),
                selector(".edge.cross").____(p("stroke-dasharray", "5 3")),
                selector(".edge.hot").____(p("stroke", v(Tok.crit)), p("opacity", 1), p("stroke-width", 2)),
                selector("#side").____(p("position", "fixed"), p("right", "16px"), p("bottom", "16px"), p("width", "min(480px, calc(100vw - 32px))"),
                        p("max-height", "70vh"), p("overflow", "auto"), p("background", v(Tok.panel)), p("border", "1px solid " + v(Tok.line)),
                        p("border-radius", "10px"), p("padding", "14px"), p("box-shadow", "0 6px 24px rgba(0,0,0,.25)")),
                selector(".detail").____(p("white-space", "pre-wrap")),
                selector(".actions form").____(p("display", "flex"), p("flex-wrap", "wrap"), p("gap", "6px"), p("margin", "8px 0")),
                selector(".actions textarea").____(p("width", "100%")),
                selector(".actions label").____(p("width", "100%"), p("font-size", "13px"), p("color", v(Tok.muted))),
                selector(".kbd").____(p("font", "600 12px ui-monospace, Consolas, monospace"), p("padding", "0 5px"), p("border", "1px solid " + v(Tok.line)), p("border-radius", "4px")),
                selector(".newitem").____(p("display", "flex"), p("flex-wrap", "wrap"), p("gap", "6px"), p("align-items", "center")),
                selector(".flash").____(p("margin", "8px 20px"), p("padding", "8px 12px"), p("border-radius", "8px"), p("background", v(Tok.hl)))
        );
    }
}
