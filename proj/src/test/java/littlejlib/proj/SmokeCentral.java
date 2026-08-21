package littlejlib.proj;

import littlejlib.proj.central.CentralChecker;
import littlejlib.proj.db.DB;

import java.util.List;


/**
 * Proves the Central lookup distinguishes published from absent, rather than saying "absent" to
 * everything - which is what a column of dashes looks like either way.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SmokeCentral" -Dexec.classpathScope=test
 */
public class SmokeCentral {

    static final List<String> COORDS = List.of(
            "com.google.code.gson:gson:2.11.0",              // certainly published
            "info.picocli:picocli:4.7.7",                    // certainly published
            "io.github.luvml:luvml:2.0",                     // ours - unknown
            "io.github.littlejlib:littlejlib-proj:1.0",           // ours - unpublished
            "com.example.definitely:not-a-real-artifact:9.9.9");

    public static void main(String[] args) throws Exception {
        var checker = new CentralChecker();
        checker.warmFromCache();

        // Poll rather than latch: request() deliberately does not fire the callback when the
        // answer is already cached, so counting down on it would wait forever on a warm cache.
        COORDS.forEach(c -> checker.request(c, done -> { }));
        var deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline
                && COORDS.stream().anyMatch(c -> checker.statusOf(c).name().equals("CHECKING"))) {
            Thread.sleep(200);
        }

        System.out.println();
        COORDS.forEach(c -> System.out.println("  " + checker.statusOf(c) + "   " + c));

        System.out.println("\nsecond pass must be served from memory with no network:");
        var t0 = System.nanoTime();
        COORDS.forEach(c -> checker.statusOf(c));
        System.out.println("  " + (System.nanoTime() - t0) / 1000 + " microseconds for " + COORDS.size() + " lookups");

        checker.shutdown();
        DB.get().shutdown();
        System.exit(0);
    }
}
