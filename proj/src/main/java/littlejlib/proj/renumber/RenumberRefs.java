package littlejlib.proj.renumber;

import littlejlib.proj.prp.PrpNumber;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decision 9: old PRP file names (of this folder, or of another folder named by its path) and {@code PRP NN} phrases
 * rewritten to the new ones. Anything that cannot be decided is left unchanged and listed for review. Inside logs only
 * file names are rewritten, and nothing is listed: a log is a record.
 */
public final class RenumberRefs {

    static final Pattern
            ANY = Pattern.compile("(?<![A-Za-z0-9])(" + PrpNumber.LEGACY + ")-prp(?![A-Za-z0-9])"),
            STRUCT = Pattern.compile("(?<num>" + PrpNumber.LEGACY + ")-prp(?:\\.(?<data>status\\.md|status\\.jsonl|meta\\.json|(?:status|meta)(?![.\\w]))"
                    + "|\\.99\\.(?<log>\\d{1,2})\\.logs\\.(?<ext>txt|md|jsonl)|(?<logs>\\.99\\.)|\\.(?<sub>\\d{1,2})\\.(?=[A-Za-z0-9])|\\.(?<serial>\\d{1,2})(?![.\\w])"
                    + "|(?<main>-)(?=[A-Za-z0-9])|(?<index>\\.index\\.)|(?<other>\\.)(?=[A-Za-z0-9]))?"),
            PHRASE = Pattern.compile("(?<![A-Za-z0-9])(PRP)([ #]|-)(\\d{1,2}|[A-HJ-NP-Z][0-9A-HJ-NP-Z])(?![A-Za-z0-9])"),
            WORD_BEFORE = Pattern.compile("([\\w./\\\\'-]+)\\s*$"),
            COMMAND = Pattern.compile("^\\s*(?:[●⎿]|\\$ )|Bash\\(|PowerShell\\(|\"command\":");

    final FolderRefs self;
    final RunRefs run;
    final boolean log;

    public RenumberRefs(FolderRefs self, RunRefs run, boolean log) {
        this.self = self;
        this.run = run;
        this.log = log;
    }

    /** The text rewritten; review items go to {@code reviews}, except inside logs where they are only counted in {@code quiet}. */
    public String rewrite(String text, String label, List<RenumberReview> reviews, int[] quiet) {
        var lines = text.split("(?<=\n)", -1);
        var out = new StringBuilder(text.length());
        var sink = new ArrayList<RenumberReview>();
        var guide = -1;
        for (var i = 0; i < lines.length; i++) {
            var indent = lines[i].length() - lines[i].stripLeading().length();
            if (guide >= 0 && !lines[i].isBlank() && indent <= guide) guide = -1;
            if (guide < 0 && lines[i].stripLeading().startsWith("- Usage Guide:")) guide = indent;
            if (guide >= 0) {
                out.append(lines[i]);
                continue;
            }
            var command = COMMAND.matcher(lines[i]).find();
            var r = new LineRef(label, i + 1, log || command ? sink : reviews);
            var l = names(lines[i], r);
            if (!log && !command) l = r.replace(l, PHRASE, this::phrase);
            out.append(l);
        }
        quiet[0] += sink.size();
        return out.toString();
    }

    String names(String line, LineRef r) {
        r.text = line;
        var m = ANY.matcher(line);
        var out = new StringBuilder();
        var at = 0;
        while (m.find(at)) {
            out.append(line, at, m.start());
            at = name(m, r, out);
        }
        return out.append(line.substring(at)).toString();
    }

    /** Appends the rewrite of the file name starting at m, returning where the rest of the line continues. */
    int name(Matcher m, LineRef r, StringBuilder out) {
        var line = r.text;
        var dir = RefPath.before(r.before(m));
        var owner = dir == null ? self : run.resolve(dir, self);
        if (owner == null || owner.unchanged) {
            if (owner == null) r.review(m, "names a file in another folder");
            out.append(m.group());
            return m.end();
        }
        var exact = owner.exact.matcher(line).region(m.start(), line.length());
        if (exact.lookingAt()) {
            out.append(owner.names.get(exact.group()));
            return exact.end();
        }
        var s = STRUCT.matcher(line).region(m.start(), line.length());
        var old = m.group(1);
        var nu = owner.numbers.get(old);
        var known = s.lookingAt();
        if (known && (s.group("index") != null || nu == null && old.equals("NN"))) {
            out.append(m.group());
            return m.end();
        }
        if (!known || nu == null || s.group("other") != null) {
            r.review(m, "names a file of PRP " + old + " that does not exist");
            out.append(m.group());
            return m.end();
        }
        if (s.group("data") != null) out.append(nu).append("01.").append(s.group("data"));
        else if (s.group("log") != null) out.append(nu).append("ZZ-").append(PrpNumber.base34(Integer.parseInt(s.group("log")), 2)).append(".logs.").append(s.group("ext"));
        else if (s.group("logs") != null) out.append(nu).append("ZZ-");
        else if (s.group("sub") != null) out.append(nu).append(slot(owner, old, s.group("sub"))).append('-');
        else if (s.group("serial") != null) out.append(nu).append(s.group("serial").equals("99") ? "ZZ" : slot(owner, old, s.group("serial")));
        else if (s.group("main") != null) out.append(nu).append("00-");
        else out.append(nu);
        return s.end();
    }

    /** The new slot of an old serial: from the map when known, else the same two digits. */
    static String slot(FolderRefs owner, String old, String serial) {
        return Objects.requireNonNullElse(owner.slot(old, serial), "%02d".formatted(Integer.parseInt(serial)));
    }

    String phrase(Matcher m, LineRef r) {
        var old = PrpNumber.parseLegacy(m.group(3));
        var owner = self;
        var before = WORD_BEFORE.matcher(r.before(m));
        if (before.find() && !before.group(1).matches(".*[.;:!?]$")) {
            var w = before.group(1).toLowerCase(Locale.ROOT).replaceAll("'s$", "").replace('\\', '/');
            var last = w.substring(w.lastIndexOf('/', w.length() - 2) + 1).replaceAll("/$", "");
            if (!last.isEmpty() && !RefPath.STOP.contains(last) && !self.self.contains(last) && !last.matches("[^a-z0-9]*")) {
                var other = run.byProjectName(last);
                if (other != null && other.unchanged) return null;
                var project = run.folderName(last) || !last.matches("[a-z]+");
                if (other == null && project && self.numbers.containsKey(old))
                    return r.review(m, "after '" + before.group(1) + "': may be another project's PRP");
                if (other != null) owner = other;
            }
        }
        var nu = owner.numbers.get(old);
        if (nu == null && !old.matches(".*\\d.*")) return null;
        if (nu == null) return r.review(m, "no PRP " + old + (owner == self ? " in this folder" : " in that project"));
        return m.group(1) + m.group(2) + nu;
    }
}
