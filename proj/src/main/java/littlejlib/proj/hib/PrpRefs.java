package littlejlib.proj.hib;

import module java.base;

import littlejlib.proj.cct.export.TranscriptEntry;
import littlejlib.proj.cct.export.TranscriptReader;

/**
 * Every PRP a transcript refers to, as (prp folder, index) or a bare {@code NN-prp-name}, with the
 * weight of who mentioned it: what the human typed counts most, what Claude did (a tool call, a reply)
 * less, what a tool printed (a listing, a grep hit) least.
 * <p>
 * Reads the conversation's entries, not the raw file: claude-code copies CLAUDE.md into every
 * transcript, and the PRP paths it cites would otherwise count in every conversation on the machine.
 */
public final class PrpRefs {

    private PrpRefs() { }

    /** @param stem {@code NN-prp-slug} as written, for a bare name; null when a folder was named */
    public record Ref(Path dir, String index, String stem, double weight, double position, boolean typed) { }

    public static final double TYPED = 5, DONE = 2, PRINTED = 1;

    static final Pattern
            ABSOLUTE = Pattern.compile("([A-Za-z]:/[^\"\\n<>|*?:]*?/prp)/(\\d{2})-prp[-.]"),
            RELATIVE = Pattern.compile("(?:^|(?<=[\\s\"'`@(\\[]))prp/(\\d{2})-prp[-.]", Pattern.MULTILINE),
            BARE = Pattern.compile("(?<![A-Za-z0-9])(\\d{2})-prp-[A-Za-z0-9_]{3,}"),
            GIT_BASH_DRIVE = Pattern.compile("(?<![A-Za-z0-9:])/([A-Za-z])/"),
            REMINDER = Pattern.compile("(?s)<system-reminder>.*?</system-reminder>");

    /** A bare name names one file: same number AND the slug as written is the start of its name. */
    public static boolean names(String stem, Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(stem.toLowerCase(Locale.ROOT));
    }

    public static List<Ref> scan(Path transcript, Path cwd) {
        List<TranscriptEntry> entries;
        try {
            entries = TranscriptReader.read(transcript);
        } catch (RuntimeException e) {
            return List.of();
        }
        var out = new ArrayList<Ref>();
        for (var i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            if (TranscriptEntry.SYSTEM.equals(e.kind()) || e.sidechain() || e.body() == null) continue;
            var weight = e.isUser() ? TYPED : TranscriptEntry.TOOL_RESULT.equals(e.kind()) ? PRINTED : DONE;
            scanOne(normal(e.body()), cwd, weight, (double) i / entries.size(), e.isUser(), out);
        }
        return out;
    }

    static String normal(String body) {
        var t = REMINDER.matcher(body).replaceAll("").replace('\\', '/').replaceAll("/{2,}", "/");
        return GIT_BASH_DRIVE.matcher(t).replaceAll("$1:/");
    }

    static void scanOne(String text, Path cwd, double weight, double position, boolean typed, List<Ref> out) {
        var named = new HashSet<Integer>();
        var m = ABSOLUTE.matcher(text);
        while (m.find()) {
            var dir = path(m.group(1));
            if (dir == null || !Files.isDirectory(dir)) continue;
            out.add(new Ref(dir, m.group(2), null, weight, position, typed));
            named.add(m.start(2));
        }
        m = RELATIVE.matcher(text);
        while (m.find()) {
            if (named.contains(m.start(1))) continue;
            out.add(new Ref(cwd.resolve("prp"), m.group(1), null, weight, position, typed));
            named.add(m.start(1));
        }
        m = BARE.matcher(text);
        while (m.find()) if (!named.contains(m.start(1))) out.add(new Ref(null, m.group(1), m.group(), weight, position, typed));
    }

    static Path path(String s) {
        try {
            return Path.of(s.replace('/', File.separatorChar)).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
