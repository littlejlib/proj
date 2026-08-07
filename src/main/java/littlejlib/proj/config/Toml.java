package littlejlib.proj.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small TOML reader and writer, for a deliberately small slice of TOML.
 * <p>
 * WHY TOML AND NOT PROPERTIES. Both are flat key-value formats; the difference that decides it is
 * escaping. Every interesting value here is a Windows path or a command template full of backslashes
 * and quotes. In a .properties file every backslash has to be doubled and every non-ASCII character
 * written as a backslash-u escape, so the file stops resembling the thing it configures and a hand
 * edit is a coin toss. TOML has LITERAL strings - anything inside single quotes is taken exactly as
 * written, with no escape processing whatsoever - so a Windows path is the path, verbatim, once.
 * <p>
 * (No backslash appears in this comment on purpose: javac expands unicode escapes before it lexes,
 * so a Windows path written inside a comment is a compile error.)
 * <p>
 * WHAT IS SUPPORTED, on purpose rather than by omission: comments, dotted keys, literal strings,
 * multi-line literal strings, basic strings with escapes, integers, booleans, and arrays of strings.
 * Table headers are accepted when reading so a hand-written file does not break, but never written -
 * this file is meant to stay flat, one setting per line.
 * <p>
 * WHAT THE WRITER GUARANTEES: it picks the string form that needs no escaping. Plain values become
 * literal strings; a value containing a single quote becomes a multi-line literal (which may contain
 * single quotes, just not three in a row); a value containing newlines becomes a multi-line literal
 * block. Only a value containing both a newline and a run of three quotes falls back to an escaped
 * basic string, and nothing here is shaped like that.
 */
public final class Toml {

    private Toml() { }

    // ---------- reading ----------

    /**
     * Parses into an ordered map of dotted key to value. Values are String, Long, Boolean or
     * List&lt;String&gt;.
     *
     * @throws IllegalArgumentException with the line number, on malformed input - a settings file
     *         that is quietly half-read is worse than one that refuses to load
     */
    public static Map<String, Object> parse(String text) {
        var out = new LinkedHashMap<String, Object>();
        var s = new Scanner(text);
        var prefix = "";

        while (true) {
            s.skipIgnorable();
            if (s.eof()) break;

            if (s.peek() == '[') {
                s.next();
                var header = new StringBuilder();
                while (!s.eof() && s.peek() != ']') header.append(s.next());
                s.expect(']');
                prefix = header.toString().trim() + ".";
                continue;
            }

            var key = s.readKey();
            s.skipSpaces();
            s.expect('=');
            s.skipSpaces();
            var value = s.readValue();
            out.put(prefix + key, value);
        }
        return out;
    }

    public static String str(Map<String, Object> map, String key) {
        var v = map.get(key);
        return v instanceof String s ? s : null;
    }

    public static int integer(Map<String, Object> map, String key, int fallback) {
        var v = map.get(key);
        if (v instanceof Long l) return l.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static List<String> list(Map<String, Object> map, String key) {
        var v = map.get(key);
        if (v instanceof List<?> l) return (List<String>) l;
        if (v instanceof String s && !s.isBlank()) return List.of(s);
        return List.of();
    }

    /** Keys under a dotted prefix, in file order, with the prefix removed. */
    public static List<String> childrenOf(Map<String, Object> map, String prefix) {
        var out = new ArrayList<String>();
        var full = prefix.endsWith(".") ? prefix : prefix + ".";
        for (var k : map.keySet()) {
            if (!k.startsWith(full)) continue;
            var rest = k.substring(full.length());
            if (!rest.isEmpty() && !out.contains(rest)) out.add(rest);
        }
        return out;
    }

    // ---------- writing ----------

    /**
     * A {@code key = value} line. The key must ALREADY be in its written form - build it with
     * {@link #dotted}, which quotes each segment as needed.
     * <p>
     * Deliberately does not quote the key itself. It used to, and quoting a key that dotted() had
     * already assembled turned {@code ide.'VS Code'} into {@code 'ide.VS Code'} - one key whose name
     * contains a dot and a space, rather than an entry named "VS Code" under "ide". It parsed, it
     * round-tripped, and it silently lost the launcher.
     */
    public static String line(String writtenKey, Object value) {
        return writtenKey + " = " + value(value);
    }

    /** A dotted key whose segments are quoted as needed, e.g. {@code terminal.'Git Bash'.open}. */
    public static String dotted(String... segments) {
        var sb = new StringBuilder();
        for (var seg : segments) {
            if (sb.length() > 0) sb.append('.');
            sb.append(keyOf(seg));
        }
        return sb.toString();
    }

    static final java.util.regex.Pattern BARE = java.util.regex.Pattern.compile("[A-Za-z0-9_-]+");

    /** One key SEGMENT, quoted only if it is not a bare key. Never given a dotted key. */
    static String keyOf(String key) {
        if (key == null || key.isEmpty()) return "''";
        return BARE.matcher(key).matches() ? key : "'" + key.replace("'", "") + "'";
    }

    public static String value(Object value) {
        if (value == null) return "''";
        if (value instanceof Boolean || value instanceof Long || value instanceof Integer) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "[]";
            var sb = new StringBuilder("[");
            for (var i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(string(String.valueOf(list.get(i))));
            }
            return sb.append("]").toString();
        }
        return string(String.valueOf(value));
    }

    /**
     * The string form that needs the least escaping, which for this file's contents means none.
     * See the class comment: a Windows path must survive a hand edit unchanged.
     */
    public static String string(String v) {
        if (v == null) return "''";

        var hasNewline = v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        var hasQuote = v.indexOf('\'') >= 0;

        // No quote and no newline: the plain literal, which is the case for every path.
        if (!hasQuote && !hasNewline) return "'" + v + "'";

        // A multi-line literal may contain single quotes, but it can neither contain three in a row
        // nor END with one - a value finishing in a quote would put four together and the parser
        // would stop in the wrong place. PowerShell templates end in '%SCRIPT%', so this is a real
        // case and not a theoretical one; it cost a round-trip failure to find.
        var tripleSafe = !v.contains("'''") && !v.endsWith("'") && !v.startsWith("'");
        if (tripleSafe && !hasNewline) return "'''" + v + "'''";
        // TOML drops the newline immediately after the opening delimiter, so one is added back.
        if (tripleSafe) return "'''\n" + v.replace("\r\n", "\n") + "'''";

        // Last resort, and the only form here that escapes anything.
        return '"' + v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }

    // ---------- the scanner ----------

    static final class Scanner {
        final String text;
        int i = 0;

        Scanner(String text) {
            // A leading BOM is invisible in an editor and turns the first key into garbage.
            this.text = text.startsWith("\uFEFF") ? text.substring(1) : text;
        }

        boolean eof() {
            return i >= text.length();
        }

        char peek() {
            return text.charAt(i);
        }

        char next() {
            return text.charAt(i++);
        }

        boolean startsWith(String s) {
            return text.startsWith(s, i);
        }

        int line() {
            var n = 1;
            for (var j = 0; j < i && j < text.length(); j++) if (text.charAt(j) == '\n') n++;
            return n;
        }

        void fail(String message) {
            throw new IllegalArgumentException("line " + line() + ": " + message);
        }

        void expect(char c) {
            if (eof() || peek() != c) fail("expected '" + c + "'");
            i++;
        }

        void skipSpaces() {
            while (!eof() && (peek() == ' ' || peek() == '\t')) i++;
        }

        /** Whitespace, newlines and comments - everything between one token and the next. */
        void skipIgnorable() {
            while (!eof()) {
                var c = peek();
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    i++;
                } else if (c == '#') {
                    while (!eof() && peek() != '\n') i++;
                } else {
                    return;
                }
            }
        }

        String readKey() {
            var sb = new StringBuilder();
            while (true) {
                skipSpaces();
                if (eof()) fail("unexpected end of file in a key");
                if (peek() == '\'' || peek() == '"') {
                    var quote = next();
                    while (!eof() && peek() != quote) sb.append(next());
                    expect(quote);
                } else {
                    var start = i;
                    while (!eof() && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '-')) i++;
                    if (i == start) fail("expected a key");
                    sb.append(text, start, i);
                }
                skipSpaces();
                if (!eof() && peek() == '.') {
                    i++;
                    sb.append('.');
                    continue;
                }
                return sb.toString();
            }
        }

        Object readValue() {
            if (eof()) fail("expected a value");

            if (startsWith("'''")) {
                i += 3;
                if (startsWith("\r\n")) i += 2;
                else if (!eof() && peek() == '\n') i++;
                var end = text.indexOf("'''", i);
                if (end < 0) fail("unterminated multi-line literal string");
                var v = text.substring(i, end);
                i = end + 3;
                return v;
            }
            if (startsWith("\"\"\"")) {
                i += 3;
                if (startsWith("\r\n")) i += 2;
                else if (!eof() && peek() == '\n') i++;
                var end = text.indexOf("\"\"\"", i);
                if (end < 0) fail("unterminated multi-line basic string");
                var v = unescape(text.substring(i, end));
                i = end + 3;
                return v;
            }
            if (peek() == '\'') {
                i++;
                var end = text.indexOf('\'', i);
                if (end < 0) fail("unterminated literal string");
                var v = text.substring(i, end);
                i = end + 1;
                return v;
            }
            if (peek() == '"') {
                i++;
                var sb = new StringBuilder();
                while (!eof() && peek() != '"') {
                    var c = next();
                    if (c == '\\' && !eof()) sb.append(escapeOf(next()));
                    else sb.append(c);
                }
                expect('"');
                return sb.toString();
            }
            if (peek() == '[') {
                i++;
                var out = new ArrayList<String>();
                while (true) {
                    skipIgnorable();
                    if (eof()) fail("unterminated array");
                    if (peek() == ']') {
                        i++;
                        return out;
                    }
                    out.add(String.valueOf(readValue()));
                    skipIgnorable();
                    if (!eof() && peek() == ',') i++;
                }
            }

            // Bare value: true, false, or a number. Anything else is almost certainly an unquoted
            // path, which is worth naming rather than storing as a mystery token.
            var start = i;
            while (!eof() && peek() != '\n' && peek() != '\r' && peek() != '#' && peek() != ','
                    && peek() != ']') i++;
            var raw = text.substring(start, i).trim();

            if (raw.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (raw.equalsIgnoreCase("false")) return Boolean.FALSE;
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                if (raw.isEmpty()) fail("expected a value");
                fail("unquoted value '" + raw + "' - wrap it in single quotes");
                return null;
            }
        }

        String escapeOf(char c) {
            return switch (c) {
                case 'n' -> "\n";
                case 'r' -> "\r";
                case 't' -> "\t";
                case '\\' -> "\\";
                case '"' -> "\"";
                case 'u' -> {
                    if (i + 4 > text.length()) yield "";
                    var hex = text.substring(i, i + 4);
                    i += 4;
                    yield String.valueOf((char) Integer.parseInt(hex, 16));
                }
                default -> String.valueOf(c);
            };
        }

        String unescape(String s) {
            var sb = new StringBuilder();
            for (var j = 0; j < s.length(); j++) {
                var c = s.charAt(j);
                if (c != '\\' || j + 1 >= s.length()) {
                    sb.append(c);
                    continue;
                }
                var n = s.charAt(++j);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    default -> sb.append(n);
                }
            }
            return sb.toString();
        }
    }
}
