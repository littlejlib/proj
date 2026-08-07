package littlejlib.proj.model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the pom.xml fields the picker shows, straight onto the record, plus the declared
 * dependencies.
 * <p>
 * Direct children of {@code <project>} only: a bare tag search picks up the groupId of the first
 * dependency instead of the project's own. groupId and version fall back to {@code <parent>},
 * which is how most child modules declare them - mm's MavenXpp3Reader left those null.
 */
public final class PomReader {

    private PomReader() { }

    /** Kept for callers that only want the record's own fields. */
    public static void applyTo(ProjectRecord rec, Path pomFile) {
        read(rec, pomFile);
    }

    /**
     * Applies the project's own coordinates to the record and returns its dependencies, from one
     * parse of one file. The ancestor chain is walked once and supplies both the properties that
     * interpolate {@code ${...}} and the dependencyManagement versions that fill in the dependencies
     * which declare none.
     */
    public static List<ProjectDependency> read(ProjectRecord rec, Path pomFile) {
        if (pomFile == null || !Files.isRegularFile(pomFile)) return List.of();
        try {
            var project = firstChild(parse(pomFile), "project");
            if (project == null) return List.of();

            var ctx = inherited(project, pomFile);

            if (rec != null) {
                var parent = firstChild(project, "parent");
                var groupId = orElse(text(project, "groupId"), text(parent, "groupId"));
                var version = orElse(text(project, "version"), text(parent, "version"));

                rec.artifactId(resolve(text(project, "artifactId"), ctx.properties))
                   .packaging(resolve(text(project, "packaging"), ctx.properties))
                   .description(resolve(text(project, "description"), ctx.properties))
                   .groupId(resolve(groupId, ctx.properties))
                   .version(resolve(version, ctx.properties));
            }

            return dependencies(project, rec == null ? null : rec.path(), ctx);
        } catch (Exception e) {
            // an unreadable or malformed pom leaves the maven fields null, which the view renders blank
            return List.of();
        }
    }

    /**
     * The declared dependencies, direct children of {@code <project><dependencies>}.
     * <p>
     * Deliberately not the ones inside {@code <dependencyManagement>}, which declare versions rather
     * than dependencies, nor the ones inside {@code <profiles>}, which are conditional - listing a
     * profile's dependencies unconditionally would report a build that nobody runs.
     */
    static List<ProjectDependency> dependencies(Element project, String projectPath, Inherited ctx) {
        var out = new ArrayList<ProjectDependency>();
        var block = firstChild(project, "dependencies");
        if (block == null) return out;

        for (var e : block.children()) {
            if (!e.tagName().equalsIgnoreCase("dependency")) continue;

            var groupId = resolve(text(e, "groupId"), ctx.properties);
            var artifactId = resolve(text(e, "artifactId"), ctx.properties);
            if (isBlank(groupId) || isBlank(artifactId)) continue;

            var declared = resolve(text(e, "version"), ctx.properties);
            var managed = false;
            if (isBlank(declared)) {
                declared = resolve(ctx.managedVersions.get(ProjectDependency.ga(groupId, artifactId)), ctx.properties);
                managed = !isBlank(declared);
            }

            out.add(new ProjectDependency()
                    .path(projectPath)
                    .ga(ProjectDependency.ga(groupId, artifactId))
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(isBlank(declared) ? null : declared)
                    .scope(resolve(text(e, "scope"), ctx.properties))
                    .optional("true".equalsIgnoreCase(text(e, "optional")))
                    .managed(managed));
        }
        return out;
    }

    static final int MAX_ANCESTORS = 3;

    /** What this pom inherits: interpolation properties and dependencyManagement versions. */
    record Inherited(Map<String, String> properties, Map<String, String> managedVersions) { }

    /**
     * This pom's {@code <properties>} and managed versions merged with its ancestors', nearest
     * winning, in a single walk up the chain.
     * <p>
     * The CI-friendly-versions layout declares {@code <revision>} once in the aggregator and every
     * module inherits {@code <version>${revision}</version>}, so a module's own properties are
     * empty and reading only those leaves the Ver column showing the literal placeholder. The same
     * applies to versions: most modules declare a dependency with no version at all and expect the
     * aggregator's dependencyManagement to supply it.
     */
    static Inherited inherited(Element project, Path pomFile) {
        var props = new HashMap<>(properties(project));
        var managed = new HashMap<>(managedVersions(project));

        var current = project;
        var currentFile = pomFile;
        for (var i = 0; i < MAX_ANCESTORS; i++) {
            var parentFile = parentPom(current, currentFile);
            if (parentFile == null) break;
            try {
                var parentProject = firstChild(parse(parentFile), "project");
                if (parentProject == null) break;
                properties(parentProject).forEach(props::putIfAbsent);            // nearest wins
                managedVersions(parentProject).forEach(managed::putIfAbsent);
                current = parentProject;
                currentFile = parentFile;
            } catch (Exception e) {
                break;
            }
        }
        return new Inherited(props, managed);
    }

    static Map<String, String> properties(Element project) {
        var out = new HashMap<String, String>();
        var props = firstChild(project, "properties");
        if (props == null) return out;
        for (var e : props.children()) out.put(e.tagName(), e.text().trim());
        return out;
    }

    /** groupId:artifactId -> version, out of {@code <dependencyManagement><dependencies>}. */
    static Map<String, String> managedVersions(Element project) {
        var out = new HashMap<String, String>();
        var block = firstChild(firstChild(project, "dependencyManagement"), "dependencies");
        if (block == null) return out;
        for (var e : block.children()) {
            if (!e.tagName().equalsIgnoreCase("dependency")) continue;
            var groupId = text(e, "groupId");
            var artifactId = text(e, "artifactId");
            var version = text(e, "version");
            if (isBlank(groupId) || isBlank(artifactId) || isBlank(version)) continue;
            out.put(ProjectDependency.ga(groupId, artifactId), version);
        }
        return out;
    }

    static Element parse(Path pomFile) throws java.io.IOException {
        return Jsoup.parse(pomFile.toFile(), "UTF-8", "", Parser.xmlParser());
    }

    static Path parentPom(Element project, Path pomFile) {
        var parent = firstChild(project, "parent");
        if (parent == null || pomFile.getParent() == null) return null;
        var rel = text(parent, "relativePath");
        var candidate = (rel == null || rel.isBlank())
                ? pomFile.getParent().resolve("../pom.xml")
                : pomFile.getParent().resolve(rel);
        if (Files.isDirectory(candidate)) candidate = candidate.resolve("pom.xml");
        return Files.isRegularFile(candidate) ? candidate.normalize() : null;
    }

    static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /** One pass, no recursion: a property whose value is itself a placeholder stays visible as one. */
    static String resolve(String value, Map<String, String> props) {
        if (value == null || value.indexOf('$') < 0 || props.isEmpty()) return value;
        var m = PLACEHOLDER.matcher(value);
        var sb = new StringBuilder();
        while (m.find()) {
            var replacement = props.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? m.group(0) : replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static Element firstChild(Element parent, String tag) {
        if (parent == null) return null;
        for (var e : parent.children()) if (e.tagName().equalsIgnoreCase(tag)) return e;
        return null;
    }

    static String text(Element parent, String tag) {
        var e = firstChild(parent, tag);
        return e == null ? null : e.text().trim();
    }

    static String orElse(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
