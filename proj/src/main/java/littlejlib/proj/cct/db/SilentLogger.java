package littlejlib.proj.cct.db;

import module java.base;
import module java.logging;

import com.arcadedb.log.Logger;

public final class SilentLogger implements Logger {

    private final PrintStream err =
        new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    @Override
    public void log(Object requester, Level level, String message, Throwable e, String context,
                    Object... args) {
        if (level == null || level.intValue() < Level.SEVERE.intValue()) return;
        err.println("arcadedb: " + safe(message, args));
        if (e != null) err.println("arcadedb: " + e);
    }

    @Override
    public void log(Object requester, Level level, String message, Throwable e, String context,
                    Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7,
                    Object a8, Object a9, Object a10, Object a11, Object a12, Object a13,
                    Object a14, Object a15, Object a16, Object a17) {
        log(requester, level, message, e, context,
            new Object[] {a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17});
    }

    @Override
    public void flush() { err.flush(); }

    private static String safe(String message, Object... args) {
        if (message == null) return "";
        if (args == null || args.length == 0) return message;
        try {
            return String.format(message, args);
        } catch (RuntimeException ignored) {
            return message;
        }
    }
}
