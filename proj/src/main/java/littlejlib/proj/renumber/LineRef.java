package littlejlib.proj.renumber;

import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One line being rewritten: a replace pass returning null keeps the match, and review() records why. */
final class LineRef {

    static final int AROUND = 80;

    final String label;
    final int line;
    final List<RenumberReview> reviews;
    String text;

    LineRef(String label, int line, List<RenumberReview> reviews) {
        this.label = label;
        this.line = line;
        this.reviews = reviews;
    }

    String replace(String in, Pattern p, BiFunction<Matcher, LineRef, String> fn) {
        text = in;
        var m = p.matcher(in);
        var out = new StringBuilder();
        while (m.find()) {
            var rep = fn.apply(m, this);
            m.appendReplacement(out, Matcher.quoteReplacement(rep == null ? m.group() : rep));
        }
        m.appendTail(out);
        return out.toString();
    }

    String before(Matcher m) {
        return text.substring(0, m.start());
    }

    String review(Matcher m, String why) {
        var from = Math.max(0, m.start() - AROUND);
        var to = Math.min(text.length(), m.end() + AROUND);
        reviews.add(new RenumberReview().file(label).line(line).text(text.substring(from, to).strip()).why(why + ": " + m.group()));
        return null;
    }
}
