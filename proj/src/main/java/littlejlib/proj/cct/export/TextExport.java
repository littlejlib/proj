package littlejlib.proj.cct.export;

import module java.base;

import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.Timestamps;

import static littlejlib.proj.cct.Num.*;

public final class TextExport {

    private TextExport() {}

    public static String render(SessionDoc s, List<TurnDoc> turns) {
        var out = new ArrayList<String>();
        out.add(MarkdownExport.title(s));
        out.add("");
        for (var f : MarkdownExport.facts(s)) out.add(f.replaceFirst("^- ", "").replace("`", ""));
        out.add("");
        for (var t : turns) {
            out.add("[" + MarkdownExport.speaker(t).toUpperCase(Locale.ROOT) + "] "
                + Timestamps.local(n(t.timestamp())) + (n(t.sidechain()) ? "  (sub-agent)" : ""));
            out.add(MarkdownExport.body(t));
            out.add("");
        }
        return String.join("\n", out);
    }
}
