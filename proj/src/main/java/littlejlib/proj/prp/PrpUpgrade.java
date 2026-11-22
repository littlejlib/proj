package littlejlib.proj.prp;

import littlejlib.proj.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Brings one PRP main file to the current head (PRP 001R): YAML front matter with the PRP's current number,
 * name, short names, uuid and the conventions line; the old Usage Guide block removed. The body is not touched.
 * The file as it was is copied into the run's backup folder first.
 */
public final class PrpUpgrade {

    /** Short names chosen beforehand (the reviewed sheet), by project folder and PRP number. */
    public record Names(String shortName, String threeLetters) { }

    public record Result(Path file, boolean changed, String was, String now) { }

    final Map<String, Names> chosen;
    final Path backupDir;
    final Map<Path, Set<String>> takenThree = new HashMap<>();

    public PrpUpgrade(Map<String, Names> chosen, Path backupDir) {
        this.chosen = chosen;
        this.backupDir = backupDir;
    }

    public static Path newBackupDir() {
        return AppPaths.home().resolve("prp-backups").resolve("upgrade-" + java.time.LocalDateTime.now().toString().replaceAll("[^0-9]", "").substring(0, 14));
    }

    public static String key(Path projectDir, String number) {
        return projectDir.toAbsolutePath().normalize().toString().toLowerCase() + "|" + number.toUpperCase();
    }

    /** Reads {@code folder TAB number TAB short TAB three} lines. */
    public static Map<String, Names> readNames(Path tsv) throws IOException {
        var out = new HashMap<String, Names>();
        for (var line : Files.readAllLines(tsv, StandardCharsets.UTF_8)) {
            var f = line.split("\t", -1);
            if (f.length < 4 || f[2].isBlank()) continue;
            out.put(key(Path.of(f[0]), f[1]), new Names(f[2].strip(), f[3].strip()));
        }
        return out;
    }

    /** A brand-new PRP file in the current head, with the names given; no backup (there is nothing to lose). */
    public static void writeNew(Path file, Names names) throws IOException {
        var number = PrpPrompt.indexOf(file);
        var project = file.getParent().getParent();
        var up = new PrpUpgrade(number == null ? Map.of() : Map.of(key(project, number), names), null);
        up.upgrade(file, false, false);
    }

    public Result upgrade(Path file, boolean dryRun) throws IOException {
        return upgrade(file, dryRun, true);
    }

    Result upgrade(Path file, boolean dryRun, boolean backup) throws IOException {
        var text = Files.readString(file, StandardCharsets.UTF_8);
        var eol = text.contains("\r\n") ? "\r\n" : "\n";
        var lines = List.of(text.split("\r?\n", -1));
        var h = PrpHeader.parse(lines);
        var was = describe(h);

        var prpDir = file.getParent();
        var project = prpDir.getParent();
        var number = PrpPrompt.indexOf(file);
        var fileName = file.getFileName().toString().replaceFirst("^[0-9A-Za-z]{4}00-", "").replaceFirst("(\\.closed)?\\.md$", "");
        var next = new PrpHeader();
        next.number = number != null ? number : h.number;
        next.name = h.name != null && !h.name.isBlank() ? h.name : fileName;
        next.uuid = h.uuid != null && Base34.valid(Base34.normalize(h.uuid)) ? Base34.normalize(h.uuid) : Base34.mint();
        next.extra.putAll(h.extra);
        var pick = number == null ? null : chosen.get(key(project, number));
        var taken = takenThree.computeIfAbsent(prpDir, d -> new HashSet<>());
        if (h.shortName != null && !h.shortName.isBlank()) {
            next.shortName = h.shortName;
            next.threeLetters = h.threeLetters;
        } else if (pick != null) {
            next.shortName = pick.shortName();
            next.threeLetters = pick.threeLetters();
        } else {
            var n = PrpShortName.derive(next.name, project.getFileName().toString(), taken);
            next.shortName = n.shortName();
            next.threeLetters = n.threeLetters();
        }
        if (next.threeLetters != null) taken.add(next.threeLetters);
        next.conventions = PrpHeader.CONVENTIONS;

        var body = new ArrayList<>(lines.subList(Math.min(h.bodyStart, lines.size()), lines.size()));
        while (!body.isEmpty() && body.get(0).isBlank()) body.remove(0);
        var out = new ArrayList<>(next.render());
        out.addAll(body);
        var newText = String.join(eol, out);
        var changed = !newText.equals(text);
        if (changed && !dryRun) {
            if (backup) {
                Files.createDirectories(backupDir);
                var copy = backupDir.resolve(file.toAbsolutePath().normalize().toString().replaceAll("[:\\\\/ ]+", "_"));
                Files.copy(file, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, newText, StandardCharsets.UTF_8);
        }
        return new Result(file, changed, was, "number " + next.number + ", short " + next.shortName + ", " + next.threeLetters
                + (h.uuid == null ? ", new uuid" : ""));
    }

    static String describe(PrpHeader h) {
        var parts = new ArrayList<String>();
        parts.add(h.yaml ? "yaml head" : "list head");
        if (h.number != null && h.number.length() < 4) parts.add("old number " + h.number);
        if (h.uuid == null) parts.add("no uuid");
        if (h.usageGuide) parts.add("Usage Guide block");
        if (h.shortName == null) parts.add("no short names");
        return String.join(", ", parts);
    }
}
