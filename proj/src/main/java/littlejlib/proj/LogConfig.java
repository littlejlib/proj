package littlejlib.proj;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Centralized logging configuration for the application.
 * Manages log levels for all third-party libraries (ArcadeDB, JavaFX, GraalVM, etc.)
 */
public class LogConfig {

    private static final Logger log = Logger.getLogger(LogConfig.class.getName());
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static boolean suppressSystemStreams = false;

    /**
     * The real console, bypassing the noise filter installed over System.out.
     * <p>
     * A command's ANSWER must never travel through {@link #isLibraryWarning}. That filter decides
     * what to drop by looking for substrings like "ArcadeDB", "javafx" and "GraalVM" anywhere in the
     * message, which is a reasonable way to recognise third-party chatter and a catastrophic way to
     * treat this tool's own output: a project list that happens to contain a JavaFX project, or a
     * PRP whose title mentions GraalVM, is discarded in full and the command appears to succeed
     * while printing nothing.
     * <p>
     * The other half of the same trap: the filtering stream overrides only println/print, so any
     * Writer wrapped around System.out writes through {@code write(byte[],int,int)} into a
     * ByteArrayOutputStream that nobody reads. Both failure modes are silent, and both are avoided
     * by writing here instead.
     */
    public static PrintStream out() {
        return suppressSystemStreams && originalOut != null ? originalOut : System.out;
    }

    public static PrintStream err() {
        return suppressSystemStreams && originalErr != null ? originalErr : System.err;
    }

    /**
     * Initialize logging configuration based on verbose mode.
     * Must be called early in application startup.
     */
    public static void initialize(boolean verbose) {
        // Save original streams BEFORE any configuration
        originalOut = System.out;
        originalErr = System.err;

        try {
            // Load base logging.properties from resources
            InputStream configStream = LogConfig.class.getClassLoader()
                .getResourceAsStream("logging.properties");

            if (configStream != null) {
                LogManager.getLogManager().readConfiguration(configStream);
                configStream.close();
            }

            if (verbose) {
                // Enable verbose logging for all components
                enableVerboseLogging();
            } else {
                // Suppress all third-party noise
                suppressThirdPartyLogging();
                suppressSystemStreams();
            }

        } catch (IOException e) {
            System.err.println("Failed to load logging configuration: " + e.getMessage());
        }
    }

    /**
     * Enable verbose logging for debugging
     */
    private static void enableVerboseLogging() {
        Logger.getLogger("").setLevel(Level.INFO);
        Logger.getLogger("com.arcadedb").setLevel(Level.INFO);
        Logger.getLogger("ArcadeDBServer").setLevel(Level.INFO);
        Logger.getLogger("javafx").setLevel(Level.INFO);

        // In verbose mode, don't suppress system streams - let everything through
        // Keep original streams as-is (already captured in initialize())
    }

    /**
     * Suppress all third-party library logging
     */
    private static void suppressThirdPartyLogging() {
        // ArcadeDB loggers
        Logger.getLogger("com.arcadedb").setLevel(Level.OFF);
        Logger.getLogger("ArcadeDBServer").setLevel(Level.OFF);
        Logger.getLogger("HttpServer").setLevel(Level.OFF);
        Logger.getLogger("PaginatedComponentFile").setLevel(Level.OFF);

        // JavaFX loggers
        Logger.getLogger("javafx").setLevel(Level.OFF);

        // GraalVM/Truffle loggers
        Logger.getLogger("com.oracle.truffle").setLevel(Level.OFF);
        Logger.getLogger("org.graalvm").setLevel(Level.OFF);

        // Root logger
        Logger.getLogger("").setLevel(Level.SEVERE);
    }

    /**
     * Suppress System.out and System.err to hide warnings from native libraries
     * that don't respect java.util.logging configuration
     */
    private static void suppressSystemStreams() {
        if (suppressSystemStreams || originalOut == null || originalErr == null) {
            return; // Already suppressed or streams not saved yet
        }

        suppressSystemStreams = true;

        final PrintStream savedOut = originalOut;
        final PrintStream savedErr = originalErr;

        // Create a selective PrintStream that only shows our terminal output
        PrintStream filteredStream = new PrintStream(new ByteArrayOutputStream()) {
            @Override
            public void println(String x) {
                // Only allow through messages that don't look like library warnings
                if (x != null && !isLibraryWarning(x)) {
                    savedOut.println(x);
                }
            }

            @Override
            public void print(String s) {
                if (s != null && !isLibraryWarning(s)) {
                    savedOut.print(s);
                }
            }

            @Override
            public void println(Object x) {
                if (x != null && !isLibraryWarning(x.toString())) {
                    savedOut.println(x);
                }
            }

            @Override
            public void flush() {
                savedOut.flush();
            }
        };

        // Redirect System.out and System.err to filtered stream
        System.setOut(filteredStream);
        System.setErr(filteredStream);
    }

    /**
     * Check if a message looks like a third-party library warning
     */
    private static boolean isLibraryWarning(String message) {
        if (message == null) return false;

        return message.contains("WARNING:")
            || message.contains("ArcadeDB")
            || message.contains("javafx")
            || message.contains("Unsupported JavaFX")
            || message.contains("Truffle")
            || message.contains("GraalVM")
            || message.contains("JVMCI")
            || message.contains("sun.misc.Unsafe")
            || message.contains("java.lang.System")
            || message.contains("JLineNativeLoader")
            || message.contains("[engine]")
            || message.contains("--enable-native-access")
            || message.contains("Restricted methods")
            || message.contains("terminally deprecated");
    }

    /**
     * Restore original System.out and System.err
     */
    public static void restoreSystemStreams() {
        if (suppressSystemStreams && originalOut != null && originalErr != null) {
            System.setOut(originalOut);
            System.setErr(originalErr);
            suppressSystemStreams = false;
        }
    }
}
