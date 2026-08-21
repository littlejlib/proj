package littlejlib.proj.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds a jbang script in a directory by looking for its directives in the head of each top-level
 * .java file. mm got this from xyz-jphil-netbeans_jbang_plugin-commons; the check is small enough
 * that carrying a dependency for it into the merged tool is not worth it.
 */
public final class JBangDetector {

    private JBangDetector() { }

    private static final List<String> MARKERS = List.of("///usr/bin/env jbang", "//DEPS", "//JAVA ", "//SOURCES");
    private static final int HEAD_LINES = 30;

    public static Path find(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        try (var files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".java"))
                    .filter(JBangDetector::hasDirective)
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean hasDirective(Path javaFile) {
        try (Stream<String> lines = Files.lines(javaFile)) {
            return lines.limit(HEAD_LINES)
                    .anyMatch(l -> MARKERS.stream().anyMatch(l.stripLeading()::startsWith));
        } catch (Exception e) {
            return false;
        }
    }
}
