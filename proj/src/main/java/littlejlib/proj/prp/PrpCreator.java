package littlejlib.proj.prp;

import littlejlib.proj.PRPTemplateLoader;
import littlejlib.proj.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Creating a PRP, with no user interface attached.
 * <p>
 * It lived inside the JavaFX dialog, which meant the only way to make a PRP was for a human to click
 * through a window - so an agent working in this repository, the very thing PRPs are written for,
 * could not create one. The rules about numbering and naming are the same either way, so they belong
 * somewhere both a window and a command line can reach.
 */
public final class PrpCreator {

    private PrpCreator() { }

    public record Created(Path file, String index, String name) { }

    /**
     * Writes a new PRP from the configured template and returns where it landed.
     *
     * @param index the number to use ({@code 07}, {@code A3}, any case), or null to take the next free one
     * @throws IOException                if the template cannot be loaded or the file cannot be written
     * @throws IllegalArgumentException   if the name is blank
     * @throws java.nio.file.FileAlreadyExistsException if that PRP already exists
     */
    public static Created create(Path prpDir, String name, String index, Settings settings) throws IOException {
        return create(prpDir, name, index, settings, false);
    }

    /**
     * @param force allow a second PRP with a name that already exists
     */
    public static Created create(Path prpDir, String name, String index, Settings settings, boolean force)
            throws IOException {
        return create(prpDir, name, index, settings, force, null);
    }

    /**
     * @param names the project's four names, needed only when the project has no proj.json yet
     */
    public static Created create(Path prpDir, String name, String index, Settings settings, boolean force,
                                 ProjJson names) throws IOException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("PRP name cannot be empty");
        var firstNames = ProjNames.gate(prpDir, names);
        Files.createDirectories(prpDir);

        // Refuse a name that is already taken at ANY index, not merely at this one. Checking only
        // the filename about to be written is no check at all when the index auto-increments: the
        // same command run twice would produce 01-prp-foo and 02-prp-foo and report success both
        // times. A caller retrying after a timeout is the normal way that happens.
        if (!force) {
            var existing = withName(prpDir, sanitize(name));
            if (existing != null) throw new java.nio.file.FileAlreadyExistsException(existing.toString());
        }

        var legacy = PrpFiles.legacyFolder(prpDir);
        var indexStr = index == null ? nextIndex(prpDir) : legacy ? PrpNumber.parseLegacy(index) : PrpNumber.parse(index);
        if (index != null && !force) {
            var used = withNumber(prpDir, indexStr);
            if (used != null) throw new IllegalArgumentException("PRP " + indexStr + " already exists: " + used);
        }
        var file = prpDir.resolve(PrpFiles.main(indexStr, sanitize(name), false));
        if (Files.exists(file)) throw new java.nio.file.FileAlreadyExistsException(file.toString());

        Files.writeString(file, template(settings)
                .replace("%index%", indexStr)
                .replace("%name%", name.trim())
                .replace("%uuid%", Base34.mint()));
        PrpUuid.ensure(file, false);
        ProjNames.write(prpDir, firstNames);
        ensureProjJson(prpDir);
        // The head in its current form (PRP 001R), short names suggested by AI (or the rule); the user renames them
        // in the file. No status markdown: the timeline is NNNN01.status.jsonl, written by proj prp note.
        var pj = ProjFile.read(prpDir.getParent());
        var projectName = pj != null && pj.name() != null ? pj.name() : prpDir.getParent().getFileName().toString();
        var chosen = PrpNames.choose(prpDir, projectName, name.trim(), file);
        PrpUpgrade.writeNew(file, new PrpUpgrade.Names(chosen.shortName(), chosen.threeLetters()));
        System.err.println("short name " + chosen.shortName() + ", 3 letters " + chosen.threeLetters() + " (by " + chosen.by() + "); rename them in the file if you like");

        return new Created(file, indexStr, name.trim());
    }

    /** A project's first PRP is when it gets a short name; a failure here never stops the PRP. */
    static void ensureProjJson(Path prpDir) {
        try {
            var project = prpDir.toAbsolutePath().normalize().getParent();
            if (project != null && "prp".equalsIgnoreCase(prpDir.getFileName().toString()))
                ProjFile.ensure(project);
        } catch (RuntimeException e) {
            System.err.println("[PrpCreator] proj.json: " + e.getMessage());
        }
    }

    /**
     * The configured template, falling back to the built-in one. A failure to reach the configured
     * source must not stop a PRP being created - the fallback is a working template, and being
     * unable to start work because a URL was unreachable would be the worse outcome.
     */
    static String template(Settings settings) throws IOException {
        try {
            return PRPTemplateLoader.loadTemplate(settings == null ? null : settings.getPrpTemplateSrc());
        } catch (IOException e) {
            return PRPTemplateLoader.loadTemplate(null);
        }
    }

    /**
     * An existing PRP with this sanitized name, at any index and open or closed, or null.
     * A closed PRP still counts: reusing its name is how two documents end up impossible to tell
     * apart in a listing.
     */
    static Path withName(Path prpDir, String sanitizedName) throws IOException {
        if (!Files.isDirectory(prpDir)) return null;
        try (var files = Files.list(prpDir)) {
            return files.filter(Files::isRegularFile).filter(p -> {
                var m = PrpFiles.mainMatcher(p.getFileName().toString());
                return m.matches() && m.group(2).equals(sanitizedName);
            }).findFirst().orElse(null);
        }
    }

    /**
     * The next free number: after the highest used by ANY PRP file, 0001 for an empty folder. A folder
     * still holding legacy files gets the next legacy number until it is renumbered.
     */
    public static String nextIndex(Path prpDir) throws IOException {
        var legacy = PrpFiles.legacyFolder(prpDir);
        var highest = highestIndex(prpDir, legacy);
        if (highest == null) return legacy ? PrpNumber.LEGACY_FIRST : PrpNumber.FIRST_NUMBER;
        var next = PrpNumber.next(highest);
        if (next == null) throw new IllegalStateException("PRP numbers are used up: " + highest + " is the last (capacity "
                + (legacy ? PrpNumber.legacyCapacity() : PrpNumber.capacity()) + ")");
        return next;
    }

    /** The highest number of any PRP file in the folder (new scheme first), or null. */
    public static String highestIndex(Path prpDir) throws IOException {
        var h = highestIndex(prpDir, false);
        return h != null ? h : highestIndex(prpDir, true);
    }

    static String highestIndex(Path prpDir, boolean legacy) throws IOException {
        if (!Files.isDirectory(prpDir)) return null;
        try (var files = Files.list(prpDir)) {
            return files.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                    .filter(n -> legacy ? PrpFiles.isLegacy(n) : PrpFiles.isNew(n))
                    .map(n -> PrpFiles.numberOf(n).toUpperCase())
                    .filter(n -> legacy ? PrpNumber.validLegacy(n) : PrpNumber.valid(n))
                    .max(Comparator.comparingInt(PrpNumber::ordinal)).orElse(null);
        }
    }

    /** A main PRP file (open or closed) with this number, or null. */
    static Path withNumber(Path prpDir, String code) throws IOException {
        if (!Files.isDirectory(prpDir)) return null;
        try (var files = Files.list(prpDir)) {
            return files.filter(Files::isRegularFile).filter(p -> {
                var n = p.getFileName().toString();
                return PrpFiles.mainMatcher(n).matches() && code.equals(PrpFiles.numberOf(n));
            }).findFirst().orElse(null);
        }
    }

    /** Lowercase, alphanumerics and separators only, spaces to underscores - the existing convention. */
    public static String sanitize(String prpName) {
        return prpName.toLowerCase()
                .replaceAll("[^a-z0-9\\s_-]", "")
                .replaceAll("\\s+", "_");
    }

    /** The status file that goes with a PRP, by the naming convention the PRP template describes. */
    public static Path statusFileFor(Path prpDir, String index) {
        return PrpFiles.statusMd(prpDir, index);
    }

    /** The n-th sub-file (n from 1), e.g. {@code 000E03-notes.md}; legacy {@code 14-prp.02.notes.md}. */
    public static Path subFileFor(Path prpDir, String index, int n, String suffix) {
        return PrpFiles.sub(prpDir, index, n, suffix == null || suffix.isBlank() ? null : sanitize(suffix), "md");
    }

    /** The prp directory of a project, which is the convention every command here assumes. */
    public static Path prpDirOf(Path projectDir) {
        return projectDir.resolve("prp");
    }

    public static List<PrpEntry> list(Path prpDir) throws IOException {
        return PrpScanner.scanPrpDirectory(prpDir);
    }
}
