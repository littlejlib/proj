package littlejlib.proj.nativeimage;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Registers every class of proj itself for reflection: constructors and fields for all (Gson and the binary store read
 * and write fields; a class missing here does not fail, gson writes {} for it), methods only where something calls
 * them reflectively: records (Gson reads them through their accessors) and the classes Jackson binds as beans.
 * Registering every method of every class kept all of proj's code reachable, UI included.
 */
public final class ProjReflectionFeature implements Feature {
    static final String ROOT = "littlejlib/proj/";

    /** Bound by Jackson through getters and setters (with their nested types). */
    static final List<String> BEANS = List.of("littlejlib.proj.ai.AiConfig", "littlejlib.proj.cct.Settings");

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        for (var name : classNames(access.getApplicationClassPath())) {
            try {
                var c = access.findClassByName(name);
                if (c == null) continue;
                RuntimeReflection.register(c);
                RuntimeReflection.register(c.getDeclaredConstructors());
                RuntimeReflection.register(c.getDeclaredFields());
                if (c.isRecord() || BEANS.stream().anyMatch(b -> name.equals(b) || name.startsWith(b + "$")))
                    RuntimeReflection.register(c.getDeclaredMethods());
                else
                    RuntimeReflection.registerAllDeclaredMethods(c); // listable, not reachable
            } catch (Throwable ignored) {
            }
        }
    }

    static List<String> classNames(List<Path> classpath) {
        var out = new ArrayList<String>();
        for (var entry : classpath) {
            try {
                if (Files.isDirectory(entry)) {
                    var root = entry.resolve(ROOT);
                    if (!Files.isDirectory(root)) continue;
                    try (var walk = Files.walk(root)) {
                        walk.map(entry::relativize).map(Path::toString).forEach(p -> add(out, p.replace('\\', '/')));
                    }
                } else if (entry.toString().endsWith(".jar")) {
                    try (var jar = new JarFile(entry.toFile())) {
                        jar.stream().forEach(e -> add(out, e.getName()));
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return out;
    }

    static void add(List<String> out, String path) {
        if (!path.startsWith(ROOT) || !path.endsWith(".class") || path.endsWith("package-info.class")) return;
        out.add(path.substring(0, path.length() - 6).replace('/', '.'));
    }
}
