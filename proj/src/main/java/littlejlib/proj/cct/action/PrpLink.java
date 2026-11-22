package littlejlib.proj.cct.action;

import module java.base;

import littlejlib.proj.cct.model.SessionDoc;

/**
 * The PRP a session was working on, recovered from how the session OPENED.
 * <p>
 * A PRP-driven session begins by attaching the file, so the first human prompt names it, and that is
 * usually the fastest way back into what the session was for.
 * <p>
 * NEVER RENUMBER WHAT THE PROMPT SAYS. A transcript records the number the PRP had when it was
 * typed, and PRPs are renumbered when projects move - this very PRP was 01 then and is 29 now.
 * <p>
 * Two things make the literal path miss on real data, and both are the normal case rather than an
 * edge:
 * <ul>
 *   <li>A CLOSED PRP HAS BEEN RENAMED. The convention appends {@code .closed} on closing, so a
 *       session that worked on {@code 01-prp-handle.md} points at a name that no longer exists -
 *       measured on a real session from August, whose file is now {@code 01-prp-handle.closed.md}.
 *       So the closed variant is tried, and the open variant for a reference that already has it.</li>
 *   <li>THE SESSION MAY HAVE RUN INSIDE THE PRP FOLDER. {@code @prp/x.md} typed from a directory
 *       itself called {@code prp} resolves against the PARENT, not the cwd.</li>
 * </ul>
 * Only a file that actually exists is ever returned, so the action is offered rather than failing.
 */
public final class PrpLink {

    private PrpLink() {}

    static final Pattern ATTACHED =
        Pattern.compile("@([A-Za-z0-9_./\\\\-]*prp[/\\\\][^\\s\"']+\\.md)", Pattern.CASE_INSENSITIVE);

    public static Path of(SessionDoc s) {
        if (s == null) return null;
        var named = mentioned(s.firstHumanPrompt());
        if (named == null) named = mentioned(s.lastPrompt());
        return named == null ? null : resolve(s.cwd(), named);
    }

    public static String mentioned(String prompt) {
        if (prompt == null || prompt.isBlank()) return null;
        var m = ATTACHED.matcher(prompt);
        return m.find() ? m.group(1) : null;
    }

    static Path resolve(String cwd, String relative) {
        var normalized = relative.replace('\\', '/');
        for (var base : bases(cwd)) {
            for (var name : names(normalized)) {
                var found = existing(base == null ? Path.of(name) : base.resolve(name));
                if (found != null) return found;
            }
        }
        return null;
    }

    static List<Path> bases(String cwd) {
        var out = new ArrayList<Path>();
        if (cwd != null && !cwd.isBlank()) {
            try {
                var here = Path.of(cwd);
                out.add(here);
                if (here.getParent() != null) out.add(here.getParent());
            } catch (Exception ignored) {
                // an unmappable path on a drive that is not mounted contributes no candidates
            }
        }
        out.add(null);
        return out;
    }

    /** The name as typed, plus the closed/open counterpart of it. */
    static List<String> names(String path) {
        if (path.endsWith(".closed.md"))
            return List.of(path, path.substring(0, path.length() - ".closed.md".length()) + ".md");
        if (path.endsWith(".md"))
            return List.of(path, path.substring(0, path.length() - ".md".length()) + ".closed.md");
        return List.of(path);
    }

    static Path existing(Path p) {
        try {
            return Files.isRegularFile(p) ? p.normalize() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The project directory to hand to proj: the session's cwd, when it still exists. */
    public static Path projectDir(SessionDoc s) {
        if (s == null || s.cwd() == null || s.cwd().isBlank()) return null;
        try {
            var p = Path.of(s.cwd());
            return Files.isDirectory(p) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }
}
