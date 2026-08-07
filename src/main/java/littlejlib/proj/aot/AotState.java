package littlejlib.proj.aot;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What jr decided about the AOT cache for this run.
 * <p>
 * jr exports JR_AOT_STATE (using / creating / off) and JR_AOT_CACHE. The environment is the
 * cheap answer, but the VM arguments are the authoritative one — a run launched by hand, or by
 * an older jr, still carries -XX:AOTCacheOutput — so that is the fallback.
 */
public final class AotState {

    private AotState() { }

    public static final String CREATING = "creating", USING = "using", OFF = "off";

    public static String state() {
        var declared = System.getenv("JR_AOT_STATE");
        if (declared != null && !declared.isBlank()) return declared.trim().toLowerCase();
        if (vmArg("-XX:AOTCacheOutput") != null) return CREATING;
        if (vmArg("-XX:AOTCache=") != null) return USING;
        return OFF;
    }

    public static boolean creating() {
        return CREATING.equals(state());
    }

    /** The cache file jr is reading or about to write, or null when AOT is not in play. */
    public static Path cache() {
        var env = System.getenv("JR_AOT_CACHE");
        if (env != null && !env.isBlank()) return Path.of(env.trim());
        var arg = vmArg("-XX:AOTCacheOutput=");
        if (arg == null) arg = vmArg("-XX:AOTCache=");
        if (arg == null) return null;
        var value = arg.substring(arg.indexOf('=') + 1).replace("\"", "");
        return value.isBlank() ? null : Path.of(value);
    }

    public static boolean cacheExists() {
        var c = cache();
        return c != null && Files.isRegularFile(c);
    }

    public static long cacheSize() {
        try {
            var c = cache();
            return c == null ? -1 : Files.size(c);
        } catch (Exception e) {
            return -1;
        }
    }

    static String vmArg(String prefix) {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith(prefix)).findFirst().orElse(null);
    }
}
