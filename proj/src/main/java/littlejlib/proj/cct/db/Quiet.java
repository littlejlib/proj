package littlejlib.proj.cct.db;

import module java.base;
import module java.logging;

import com.arcadedb.log.LogManager;

public final class Quiet {

    private Quiet() {}

    private static final List<String> NOISY = List.of(
        "com.arcadedb", "com.arcadedb.server", "com.arcadedb.engine",
        "io.undertow", "org.xnio", "org.jboss");

    public static void suppress() {
        LogManager.instance().setLogger(new SilentLogger());
        for (var n : NOISY) Logger.getLogger(n).setLevel(Level.SEVERE);
        Logger.getLogger("").setLevel(Level.WARNING);
    }
}
