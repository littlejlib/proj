package littlejlib.proj.prp;

import littlejlib.proj.PRPTemplateLoader;
import littlejlib.proj.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** Existing PRP files are matched on this, which is also what fixes the next index. */
    static final Pattern PRP_FILE = Pattern.compile("^(\\d{2})-prp-.+\\.md$");

    public record Created(Path file, String index, String name) { }

    /**
     * Writes a new PRP from the configured template and returns where it landed.
     *
     * @param index the two-digit number to use, or null to take the next one after the highest present
     * @throws IOException                if the template cannot be loaded or the file cannot be written
     * @throws IllegalArgumentException   if the name is blank
     * @throws java.nio.file.FileAlreadyExistsException if that PRP already exists
     */
    public static Created create(Path prpDir, String name, Integer index, Settings settings) throws IOException {
        return create(prpDir, name, index, settings, false);
    }

    /**
     * @param force allow a second PRP with a name that already exists
     */
    public static Created create(Path prpDir, String name, Integer index, Settings settings, boolean force)
            throws IOException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("PRP name cannot be empty");
        Files.createDirectories(prpDir);

        // Refuse a name that is already taken at ANY index, not merely at this one. Checking only
        // the filename about to be written is no check at all when the index auto-increments: the
        // same command run twice would produce 01-prp-foo and 02-prp-foo and report success both
        // times. A caller retrying after a timeout is the normal way that happens.
        if (!force) {
            var existing = withName(prpDir, sanitize(name));
            if (existing != null) throw new java.nio.file.FileAlreadyExistsException(existing.toString());
        }

        var n = index != null ? index : nextIndex(prpDir);
        var indexStr = String.format("%02d", n);
        var file = prpDir.resolve(indexStr + "-prp-" + sanitize(name) + ".md");
        if (Files.exists(file)) throw new java.nio.file.FileAlreadyExistsException(file.toString());

        Files.writeString(file, template(settings)
                .replace("%index%", indexStr)
                .replace("%name%", name.trim()));

        return new Created(file, indexStr, name.trim());
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
        var pattern = Pattern.compile("^\\d{2}-prp-" + Pattern.quote(sanitizedName) + "(\\.closed)?\\.md$");
        try (var files = Files.list(prpDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> pattern.matcher(p.getFileName().toString()).matches())
                    .findFirst().orElse(null);
        }
    }

    public static int nextIndex(Path prpDir) throws IOException {
        return highestIndex(prpDir) + 1;
    }

    static int highestIndex(Path prpDir) throws IOException {
        if (!Files.isDirectory(prpDir)) return 0;
        try (var files = Files.list(prpDir)) {
            return files.filter(Files::isRegularFile)
                    .map(p -> PRP_FILE.matcher(p.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(m -> Integer.parseInt(m.group(1)))
                    .max(Integer::compareTo).orElse(0);
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
        return prpDir.resolve(index + "-prp.status.md");
    }

    /** A numbered sub-file, e.g. {@code 14-prp.02.notes.md}. */
    public static Path subFileFor(Path prpDir, String index, int n, String suffix) {
        var tail = suffix == null || suffix.isBlank() ? "" : "." + sanitize(suffix);
        return prpDir.resolve(index + "-prp." + String.format("%02d", n) + tail + ".md");
    }

    /** The prp directory of a project, which is the convention every command here assumes. */
    public static Path prpDirOf(Path projectDir) {
        return projectDir.resolve("prp");
    }

    public static List<PrpEntry> list(Path prpDir) throws IOException {
        return PrpScanner.scanPrpDirectory(prpDir);
    }
}
