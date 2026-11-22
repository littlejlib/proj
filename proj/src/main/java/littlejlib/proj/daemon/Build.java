package littlejlib.proj.daemon;

import java.io.File;

/**
 * Identifies the code this process is running.
 * <p>
 * The client sends it with every request and the daemon compares. A rebuilt jar under a running
 * daemon is the commonest failure of every resident tool here - it keeps serving the old code and
 * the symptom is "my fix did nothing". Detecting it costs one string.
 */
public final class Build {

    private Build() { }

    public static final String STAMP = compute();

    static String compute() {
        try {
            var location = Build.class.getProtectionDomain().getCodeSource().getLocation();
            var file = new File(location.toURI());
            return file.isFile()
                    ? "jar:" + file.length() + ":" + file.lastModified()
                    : "classes:" + file.lastModified();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** The jar to relaunch, or null when running from a classes directory in an IDE. */
    public static File jar() {
        try {
            var file = new File(Build.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return file.isFile() ? file : null;
        } catch (Exception e) {
            return null;
        }
    }
}
