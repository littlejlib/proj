package littlejlib.proj.util;

import java.lang.foreign.*;
import java.nio.file.Path;

/** Is this process a GraalVM native image, where is its exe, and the one Windows call only that exe needs. */
public final class Native {
    private Native() { }

    public static boolean image() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    public static Path exe() {
        return ProcessHandle.current().info().command().map(Path::of).orElse(null);
    }

    /** A resident process leaves the console it was started from, so closing that terminal does not end it. */
    public static void leaveConsole() {
        if (!image() || !System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            var kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            var free = Linker.nativeLinker().downcallHandle(kernel32.find("FreeConsole").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));
            var done = (int) free.invokeExact();
        } catch (Throwable t) {
            System.err.println("[proj] could not leave the console: " + t);
        }
    }
}
