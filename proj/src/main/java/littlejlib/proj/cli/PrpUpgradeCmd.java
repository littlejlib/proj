package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpUpgrade;
import littlejlib.proj.track.GlobalIndex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Brings PRP files to the current head (YAML front matter, short names, uuid; old Usage Guide removed). */
@Command(name = "upgrade", description = {
        "Bring PRP main files to the current head: YAML front matter (prp_number, prp_name, prp_3_letter_name,",
        "prp_short_name, prp_uuid, conventions); the old Usage Guide block removed; the body untouched.",
        "Every file is copied to proj's prp-backups/upgrade-<time>/ first. --dry-run changes nothing."})
public class PrpUpgradeCmd implements Runnable {

    @Parameters(arity = "0..*", description = "PRP main files (omit with --all)")
    List<Path> files;

    @Option(names = "--all", description = "Every PRP proj knows (proj prp find's index)")
    boolean all;

    @Option(names = "--names", description = "TSV of chosen short names: folder TAB number TAB short name TAB 3 letters")
    Path names;

    @Option(names = "--dry-run", description = "Report what would change; write nothing")
    boolean dryRun;

    @Option(names = "--report", description = "Also write the report (TSV) to this file")
    Path report;

    @Override
    public void run() {
        TrackCli.run(() -> {
            var targets = new ArrayList<Path>();
            if (files != null) targets.addAll(files);
            if (all) for (var l : GlobalIndex.read()) if (l.file() != null) targets.add(Path.of(l.file()));
            if (targets.isEmpty()) throw new IllegalArgumentException("name PRP files, or --all");
            var chosen = names == null ? Map.<String, PrpUpgrade.Names>of() : PrpUpgrade.readNames(names);
            var backup = PrpUpgrade.newBackupDir();
            var up = new PrpUpgrade(chosen, backup);
            var lines = new ArrayList<String>();
            lines.add("file\tchanged\twas\tnow");
            Map<String, Integer> counts = new LinkedHashMap<>();
            int changed = 0, failed = 0;
            for (var f : targets) {
                if (!Files.isRegularFile(f)) {
                    lines.add(f + "\tmissing\t\t");
                    counts.merge("missing", 1, Integer::sum);
                    continue;
                }
                try {
                    var r = up.upgrade(f, dryRun);
                    if (r.changed()) changed++;
                    for (var part : r.was().split(", ")) counts.merge(part, 1, Integer::sum);
                    lines.add(f + "\t" + (r.changed() ? "yes" : "no") + "\t" + r.was() + "\t" + r.now());
                } catch (Exception e) {
                    failed++;
                    lines.add(f + "\tFAILED\t" + e.getMessage() + "\t");
                }
            }
            if (report != null) Files.write(report, lines, StandardCharsets.UTF_8);
            if (targets.size() <= 20) lines.forEach(PrpCommands.OUT::println);
            PrpCommands.OUT.println((dryRun ? "would change " : "changed ") + changed + " of " + targets.size() + " PRP files"
                    + (failed > 0 ? ", " + failed + " FAILED" : "") + (dryRun || changed == 0 ? "" : "; originals in " + backup));
            counts.forEach((k, v) -> PrpCommands.OUT.println("  " + v + "  " + k));
        });
    }
}
