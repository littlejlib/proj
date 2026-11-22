package littlejlib.proj.prp;

import littlejlib.proj.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The PRP conventions that {@code proj prp guide} prints, so a PRP file needs one line instead of a
 * long usage block and the convention moves with proj. Resolution: start with the copy inside proj, apply
 * the global list ({@code prpGuide} in the settings) in order, then the project's list
 * ({@code prpGuide} in {@code prp/proj.json}) in order. An {@code extend} entry is printed after what came
 * before, a {@code replace} entry instead of all of it. Entry syntax is in {@link GuideSource}.
 */
public final class PrpGuide {

    private PrpGuide() { }

    public static final String BUNDLED = "bundled", RESOURCE = "/prp-guide/prp-guide.md";

    /** The combined text and one line per source naming where each part came from. */
    public record Guide(String text, List<String> origins) { }

    static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** The entries in effect for a project, global first. */
    public static List<GuideSource> sources(Path project, Settings settings) {
        var entries = new ArrayList<String>();
        if (settings != null && settings.getPrpGuide() != null) entries.addAll(settings.getPrpGuide());
        var p = project == null ? null : ProjFile.read(project);
        if (p != null && p.prpGuide() != null) entries.addAll(p.prpGuide());
        return entries.stream().filter(e -> e != null && !e.isBlank()).map(GuideSource::parse).toList();
    }

    public static Guide load(Path project, Settings settings, boolean refresh) throws IOException {
        var parts = new ArrayList<Guide>();
        var notes = new ArrayList<String>();
        parts.add(new Guide(bundled(), List.of("guide source: the copy inside proj (the base)")));
        for (var s : sources(project, settings)) {
            try {
                var g = read(s, project, refresh);
                if (s.replace()) parts.clear();
                parts.add(g);
            } catch (IOException | RuntimeException e) {
                notes.add("guide source SKIPPED: " + s.shown() + " could not be read (" + e.getMessage() + ") and no local copy exists; the guide above does not include it");
            }
        }
        var text = parts.stream().map(g -> g.text().stripTrailing()).collect(Collectors.joining("\n\n")) + "\n";
        var origins = new ArrayList<String>();
        parts.forEach(g -> origins.addAll(g.origins()));
        origins.addAll(notes);
        return new Guide(text, origins);
    }

    static Guide read(GuideSource s, Path project, boolean refresh) throws IOException {
        var mode = (s.replace() ? "replace " : "extend ") + s.shown();
        if (s.isBundled()) return new Guide(bundled(), List.of("guide source: " + mode + " - the copy inside proj"));
        if (s.isHttp()) {
            var c = GithubCache.get(s.spec(), refresh);
            return new Guide(c.text(), List.of("guide source: " + mode + " - the text above is a LOCAL COPY of this online file: " + local(c.file(), c.fetchedAt(), c.fresh())));
        }
        if (s.isGit()) {
            var f = s.gitFile();
            if (f == null) throw new IOException("not a git source: use git@host:org/repo.git#ref:path or git+https://github.com/org/repo/blob/ref/path");
            var c = GitCache.get(f, refresh);
            var warn = c.warn() == null ? "" : "; WARNING: " + c.warn();
            return new Guide(c.text(), List.of("guide source: " + mode + " (fetched with git) - the text above is a LOCAL COPY of this repository file: " + local(c.file(), c.fetchedAt(), c.fresh()) + warn));
        }
        var file = Path.of(s.spec());
        if (!file.isAbsolute() && project != null) file = project.resolve(file);
        return new Guide(Files.readString(file, StandardCharsets.UTF_8), List.of("guide source: " + mode + " - local file " + file));
    }

    static String local(Path file, long fetchedAt, boolean fresh) {
        return file + " (fetched " + SHOWN.format(Instant.ofEpochMilli(fetchedAt)) + (fresh ? ", just now" : "") + ")";
    }

    public static String bundled() throws IOException {
        try (var in = PrpGuide.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("the PRP guide is missing from this build: " + RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
