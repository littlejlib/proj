package littlejlib.proj.ide;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the editors and IDEs actually installed, so the picker offers what exists rather than what
 * a config file once claimed. PATH first, then the usual install locations.
 * <p>
 * Detection is cached for the life of the process: it stats a few dozen paths, which is nothing
 * once but not something to repeat per rendered cell.
 */
public final class IdeDetector {

    private IdeDetector() { }

    static volatile List<DetectedIde> cached;

    public static List<DetectedIde> detect() {
        var local = cached;
        if (local == null) {
            local = scan();
            cached = local;
        }
        return local;
    }

    public static void forget() {
        cached = null;
    }

    static List<DetectedIde> scan() {
        var found = new ArrayList<DetectedIde>();
        for (var candidate : CANDIDATES.entrySet()) {
            var spec = candidate.getValue();
            var exe = locate(spec.executables);
            if (exe != null) found.add(new DetectedIde(candidate.getKey(), exe, spec.args, spec.folders));
        }
        return found;
    }

    /**
     * Delegated to the shared locator, which also searches the siblings of PATH entries - an IDE
     * installed outside Program Files was invisible here for the same reason Tabby was.
     */
    static Path locate(List<String> executables) {
        return littlejlib.proj.util.ToolLocator.locate(executables);
    }

    static final Map<String, Spec> CANDIDATES = new LinkedHashMap<>(Map.of());

    static {
        CANDIDATES.put("VS Code", new Spec(List.of("Code.exe", "code.exe", "code.cmd", "code"), "\"%PATH%\"", true));
        CANDIDATES.put("NetBeans", new Spec(List.of("netbeans64.exe", "netbeans.exe", "netbeans"),
                "--console suppress --open \"%PATH%\"", true));
        CANDIDATES.put("IntelliJ", new Spec(List.of("idea64.exe", "idea.exe", "idea"), "\"%PATH%\"", true));
        CANDIDATES.put("Notepad++", new Spec(List.of("notepad++.exe"), "\"%PATH%\"", false));
        CANDIDATES.put("Notepad", new Spec(List.of("notepad.exe"), "\"%PATH%\"", false));
    }
}
