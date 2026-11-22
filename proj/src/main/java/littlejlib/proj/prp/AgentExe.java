package littlejlib.proj.prp;

import module java.base;

/**
 * Which claude: {@code --exe fclaude} replaces the program the agent template runs, for one launch.
 * <p>
 * It is an explicit override and never a standing choice, so there is no setting for it. The command
 * keeps the BARE name and the shell finds the right file, exactly as it does for {@code claude};
 * searching PATH here is only to refuse a name that is not there, early, and to list what is.
 */
public final class AgentExe {

    private AgentExe() { }

    static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");
    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** The default program, which is what an unset exe means. */
    public static final String DEFAULT = "claude";

    /** The trimmed name, null for null or blank. Refuses anything that is not a plain program name. */
    public static String check(String exe) {
        if (exe == null || exe.isBlank()) return null;
        var e = exe.trim();
        if (!NAME.matcher(e).matches()) throw new IllegalArgumentException("not a program name: " + exe
                + "   (a bare name such as fclaude, no path and no extension needed)");
        return e;
    }

    /** {@link #check} that also insists the program is on PATH. */
    public static String require(String exe) {
        var e = check(exe);
        if (e == null) return null;
        if (resolve(e) == null) {
            var found = candidates();
            throw new IllegalArgumentException("no program named " + e + " on PATH"
                    + (found.isEmpty() ? "" : "   (claude variants found: " + String.join(", ", found) + ")"));
        }
        return e;
    }

    /** Whether this names the default program, so a record of it needs no question when resuming. */
    public static boolean isDefault(String exe) {
        var e = exe == null ? "" : exe.trim();
        return e.isEmpty() || e.equalsIgnoreCase(DEFAULT);
    }

    /** The file PATH would run for this name, or null. */
    public static Path resolve(String name) {
        for (var dir : pathDirs()) {
            for (var ext : extensions(name)) {
                var f = dir.resolve(name + ext);
                if (Files.isRegularFile(f) && (WINDOWS || Files.isExecutable(f))) return f;
            }
        }
        return null;
    }

    /** Every {@code *claude} program on PATH: claude, fclaude, pclaude and any other variant, claude first. */
    public static List<String> candidates() {
        var found = new TreeSet<String>();
        for (var dir : pathDirs()) {
            try (var files = Files.list(dir)) {
                files.forEach(f -> {
                    var base = baseName(f.getFileName().toString());
                    if (base != null && base.toLowerCase(Locale.ROOT).endsWith("claude") && Files.isRegularFile(f))
                        found.add(base);
                });
            } catch (Exception e) {
                // an unreadable or missing PATH entry is not worth failing a lookup over
            }
        }
        var out = new ArrayList<String>();
        if (found.remove(DEFAULT)) out.add(DEFAULT);
        out.addAll(found);
        return out;
    }

    /** The template with its program replaced; unchanged for a blank exe. */
    public static String withExe(String template, String exe) {
        var e = check(exe);
        if (e == null) return template;
        var t = template.stripLeading();
        var end = 0;
        if (t.startsWith("\"")) {
            var q = t.indexOf('"', 1);
            end = q < 0 ? t.length() : q + 1;
        } else {
            while (end < t.length() && !Character.isWhitespace(t.charAt(end))) end++;
        }
        return e + t.substring(end);
    }

    static List<Path> pathDirs() {
        var dirs = new ArrayList<Path>();
        var path = System.getenv("PATH");
        if (path == null) return dirs;
        for (var s : path.split(File.pathSeparator)) {
            if (s.isBlank()) continue;
            try {
                dirs.add(Path.of(s.trim().replace("\"", "")));
            } catch (Exception e) {
                // a malformed PATH entry is skipped
            }
        }
        return dirs;
    }

    /** A name given with its extension is tried as it is first, then with each runnable extension. */
    static List<String> extensions(String name) {
        var out = new ArrayList<String>();
        if (WINDOWS) {
            var pathext = System.getenv("PATHEXT");
            if (pathext == null || pathext.isBlank()) pathext = ".COM;.EXE;.BAT;.CMD";
            var lower = name.toLowerCase(Locale.ROOT);
            for (var x : pathext.split(";")) {
                if (x.isBlank()) continue;
                if (lower.endsWith(x.toLowerCase(Locale.ROOT))) out.add("");
            }
            for (var x : pathext.split(";")) if (!x.isBlank()) out.add(x.trim());
        } else {
            out.add("");
            out.add(".sh");
        }
        return out;
    }

    /** The program name a file stands for: the name without its runnable extension, or null if it is not runnable by name. */
    static String baseName(String file) {
        var lower = file.toLowerCase(Locale.ROOT);
        if (WINDOWS) {
            for (var x : List.of(".exe", ".cmd", ".bat", ".com"))
                if (lower.endsWith(x)) return file.substring(0, file.length() - x.length());
            return null;
        }
        return lower.endsWith(".sh") ? file.substring(0, file.length() - 3) : file;
    }
}
