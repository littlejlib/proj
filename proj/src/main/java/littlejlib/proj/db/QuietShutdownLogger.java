package littlejlib.proj.db;

import com.arcadedb.log.DefaultLogger;
import com.arcadedb.log.LogManager;
import com.arcadedb.log.Logger;

import java.util.logging.Level;

/** ArcadeDB prints its shutdown-hook lines to stderr on every JVM exit; errors carry a throwable and still pass. */
final class QuietShutdownLogger implements Logger {

    final Logger delegate;

    QuietShutdownLogger(Logger delegate) { this.delegate = delegate; }

    static void install() { LogManager.instance().setLogger(new QuietShutdownLogger(new DefaultLogger())); }

    static boolean quiet(Throwable t) { return t == null && DefaultLogger.isShuttingDown(); }

    @Override public void log(Object r, Level l, String m, Throwable t, String c, Object[] a) {
        if (!quiet(t)) delegate.log(r, l, m, t, c, a);
    }

    @Override public void log(Object r, Level l, String m, Throwable t, String c,
            Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7, Object a8, Object a9,
            Object a10, Object a11, Object a12, Object a13, Object a14, Object a15, Object a16, Object a17) {
        if (!quiet(t)) delegate.log(r, l, m, t, c, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17);
    }

    @Override public void flush() { delegate.flush(); }
}
