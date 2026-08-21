package littlejlib.proj;

import littlejlib.proj.prp.GlobalPrpScanner;

import java.nio.file.Path;

/**
 * Times both ways of finding every PRP on this machine, and says what each found.
 * <p>
 * The two are not interchangeable and the numbers are the reason. The tree walk is what the global
 * viewer has always used; measured here at 8.9s for the primary code base and 15.3s for the mirror,
 * which is twenty-four seconds to fill a tab someone has just clicked. Statting {@code <project>/prp}
 * for each project already in the index answers the same question from data proj has anyway.
 * <p>
 * It also catches the failure that made this necessary: one prp folder holding a status file whose
 * PRP had been renamed threw out of the whole scan, so a single orphan silently emptied the list for
 * every project after it.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.SmokeGlobalScan" -Dexec.classpathScope=test
 */
public class SmokeGlobalScan {

    public static void main(String[] args) {
        var settings = new SettingsManager().loadSettings();

        for (var base : settings.getBrowsePaths()) {
            var t0 = System.currentTimeMillis();
            var found = GlobalPrpScanner.scanAllProjects(Path.of(base), settings.getProjectScanningDepth());
            System.out.println("walk   " + base + "  ->  " + found.size()
                    + " PRPs in " + (System.currentTimeMillis() - t0) + " ms");
        }

        // The fast path needs a list of projects. Two levels of directory listing stands in for the
        // index here, which is what the picker passes in the real window.
        var dirs = new java.util.ArrayList<Path>();
        for (var base : settings.getBrowsePaths()) {
            for (var org : children(Path.of(base))) {
                dirs.add(org);
                dirs.addAll(children(org));
            }
        }
        var t0 = System.currentTimeMillis();
        var found = GlobalPrpScanner.scanProjects(dirs);
        System.out.println("index  " + dirs.size() + " known directories  ->  " + found.size()
                + " PRPs in " + (System.currentTimeMillis() - t0) + " ms");

        System.out.println("\nten most recently active:");
        found.stream().limit(10).forEach(e -> System.out.println("   " + e.getLastModifiedDisplay()
                + "  " + e.getProjectName() + " / " + e.getIndex() + " " + e.getName()));
    }

    static java.util.List<Path> children(Path dir) {
        try (var s = java.nio.file.Files.list(dir)) {
            return s.filter(java.nio.file.Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith(".")).toList();
        } catch (Exception e) {
            return java.util.List.of();
        }
    }
}
