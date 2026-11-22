package littlejlib.proj.prp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The uuid of a PRP lives in the PRP file itself, as the line {@code - PRP UUID: <26 base34 symbols>}
 * under {@code - PRP Name:}. The file is what moves and gets renamed (open to closed included), so the
 * identity travels with it. Everything else (index.jsonl, status.jsonl) only refers to it.
 */
public final class PrpUuid {

    private PrpUuid() { }

    static final String LABEL = "- PRP UUID: ";

    /** The old list line or the YAML front matter key (PRP 001R). */
    static final Pattern LINE = Pattern.compile("^(?:- PRP UUID:|prp_uuid:)[ \\t]*\"?([^\\s\"]*)\"?[ \\t]*$");

    /** How far down the file the line is looked for: it is part of the first lines or it is not there. */
    static final int HEAD = 40;

    /** What {@link #ensure} found or did. {@code inserted} is true when the line was written. */
    public record Found(String uuid, boolean inserted) { }

    /** The uuid in the file's first lines in written form, or null when there is none or it is not valid. */
    public static String read(Path prpFile) {
        try (var lines = Files.lines(prpFile, StandardCharsets.UTF_8)) {
            return lines.limit(HEAD).map(LINE::matcher).filter(java.util.regex.Matcher::matches)
                    .map(m -> Base34.normalize(m.group(1))).filter(Base34::valid).findFirst().orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The file's text with the uuid line in place; the text itself when it already has a valid one. */
    public static String with(String text, String uuid) {
        var eol = text.contains("\r\n") ? "\r\n" : "\n";
        var lines = text.split("\r?\n", -1);
        for (var i = 0; i < Math.min(lines.length, HEAD); i++) {
            var m = LINE.matcher(lines[i]);
            if (m.matches()) {
                if (Base34.valid(Base34.normalize(m.group(1)))) return text;
                lines[i] = (lines[i].startsWith("prp_uuid") ? "prp_uuid: " : LABEL) + uuid;
                return String.join(eol, lines);
            }
        }
        if (lines.length > 0 && lines[0].strip().equals("---")) {
            for (var i = 1; i < Math.min(lines.length, HEAD); i++) if (lines[i].strip().equals("---")) {
                var out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
                out.add(i, "prp_uuid: " + uuid);
                return String.join(eol, out);
            }
        }
        var at = after(lines, "- PRP Name:");
        if (at < 0) at = after(lines, "- PRP Number:");
        var out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
        out.add(at + 1, LABEL + uuid);
        return String.join(eol, out);
    }

    /** The index of the first header line starting with {@code prefix} within the head, or -1. */
    private static int after(String[] lines, String prefix) {
        for (var i = 0; i < Math.min(lines.length, HEAD); i++) if (lines[i].startsWith(prefix)) return i;
        return -1;
    }

    /**
     * The uuid of this PRP, written into the file first if it has none. The previous file is kept in
     * the backup folder before it is touched, unless the file is brand new ({@code backup} false).
     */
    public static Found ensure(Path prpFile, boolean backup) throws IOException {
        var have = read(prpFile);
        if (have != null) return new Found(have, false);
        var uuid = Base34.mint();
        var text = Files.readString(prpFile, StandardCharsets.UTF_8);
        if (backup) PrpFileEdit.backup(prpFile);
        Files.writeString(prpFile, with(text, uuid), StandardCharsets.UTF_8);
        return new Found(uuid, true);
    }

    /** {@link #ensure} for the lazy callers: a failure is a note on stderr, never a reason to stop. */
    public static String ensureQuiet(Path prpFile) {
        try {
            return ensure(prpFile, true).uuid();
        } catch (IOException | RuntimeException e) {
            System.err.println("[PrpUuid] " + prpFile.getFileName() + ": " + e.getMessage());
            return null;
        }
    }
}
