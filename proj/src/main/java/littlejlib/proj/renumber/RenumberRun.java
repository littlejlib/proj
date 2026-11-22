package littlejlib.proj.renumber;

import littlejlib.proj.prp.PrpFileEdit;
import littlejlib.proj.track.PrpIndex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static littlejlib.proj.renumber.Renumber.*;

/**
 * One run over several prp folders, in two phases: (1) every folder's mapping (written to its renumber-map.jsonl, or
 * only printed in a dry run); (2) renames and reference rewrites with ALL the run's maps, and those of folders
 * renumbered earlier, so a path into another folder is rewritten with that folder's map.
 */
public final class RenumberRun {

    private RenumberRun() { }

    /** Logs (slot ZZ), and sub-files that are transcripts in all but name. */
    static final Pattern LOG = Pattern.compile("^[0-9A-HJ-NP-Z]{4}ZZ-|^[0-9A-Z]{2}-prp\\.99\\.|(?i)transcript|conversation-log");

    public static List<RenumberResult> run(List<Path> prpDirs, boolean dryRun) throws IOException {
        var run = new RunRefs();
        var results = new ArrayList<RenumberResult>();
        var refs = new IdentityHashMap<RenumberResult, FolderRefs>();
        for (var dir : prpDirs) {
            var result = new RenumberResult().folder(dir.toString());
            results.add(result);
            var self = self(dir.toAbsolutePath().normalize().getParent());
            try {
                result.plan(Planner.plan(dir));
            } catch (IllegalStateException e) {
                result.state("refused").why(e.getMessage());
                run.add(new FolderRefs(dir, self, List.of(), true));
                continue;
            }
            if (result.plan().files().isEmpty()) {
                result.state("new").why("already in the new scheme");
                var earlier = RunRefs.fromMap(dir);
                run.add(earlier != null ? earlier : new FolderRefs(dir, self, List.of(), false));
                continue;
            }
            var f = new FolderRefs(dir, self, result.plan().files(), false);
            run.add(f);
            refs.put(result, f);
            if (!dryRun) writeLines(dir.resolve(MAP), result.plan().files());
        }
        for (var result : results)
            if (refs.containsKey(result)) apply(Path.of(result.folder()), result, refs.get(result), run, dryRun);
        return results;
    }

    static void apply(Path prpDir, RenumberResult result, FolderRefs self, RunRefs run, boolean dryRun) throws IOException {
        var plan = result.plan();
        var project = prpDir.toAbsolutePath().normalize().getParent();
        var renamed = plan.files().stream().collect(Collectors.toMap(RenumberFile::oldFile, RenumberFile::newFile));
        if (!dryRun) for (var f : plan.files()) Files.move(prpDir.resolve(f.oldFile()), prpDir.resolve(f.newFile()));
        var quiet = new int[1];
        for (var f : texts(prpDir, project)) {
            var name = f.getFileName().toString();
            var inPrp = f.getParent().equals(prpDir);
            var now = dryRun && inPrp ? renamed.getOrDefault(name, name) : name;
            var label = label(project, inPrp ? prpDir.resolve(now) : f);
            var text = read(f, label, result.reviews());
            if (text == null) continue;
            var out = new RenumberRefs(self, run, inPrp && LOG.matcher(now).find()).rewrite(text, label, result.reviews(), quiet);
            if (out.equals(text)) continue;
            result.rewritten().add(label);
            if (dryRun) continue;
            PrpFileEdit.backup(f);
            Files.writeString(f, out, StandardCharsets.UTF_8);
        }
        result.quiet(quiet[0]);
        if (dryRun) {
            result.state("dry-run");
            return;
        }
        recordWas(prpDir, plan);
        if (!result.reviews().isEmpty()) writeLines(prpDir.resolve(REVIEW), result.reviews());
        PrpIndex.write(prpDir, PrpIndex.build(prpDir));
        result.state("done");
    }
}
