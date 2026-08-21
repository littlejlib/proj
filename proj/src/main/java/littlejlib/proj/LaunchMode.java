package littlejlib.proj;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * jr.exe chooses java.exe-style (console attached) or javaw.exe-style (console freed)
 * before the JVM starts, and that choice decides whether handing a path back to a shell
 * means anything at all. With no console there is no shell listening, so navigation has
 * to become "open a terminal there" instead of "print a path".
 *
 * JR_LAUNCH_MODE is jr's own answer and is trusted when set. Without it we probe, which
 * works because jr's GUI path calls FreeConsole and repoints the std streams at the bit
 * bucket -- so an absent console genuinely means GUI, not merely a redirected one.
 *
 * Handles are bound lazily so this class stays loadable on the macOS build.
 */
public final class LaunchMode {

    static MethodHandle GetStdHandle, GetFileType, GetConsoleWindow;

    /** True when a real console window is attached, i.e. a human is watching a terminal. */
    public static boolean hasConsole() {
        var declared = System.getenv("JR_LAUNCH_MODE");
        if (declared != null && !declared.isBlank()) return "console".equalsIgnoreCase(declared.trim());
        if (!windows()) return System.console() != null;
        try {
            bind();
            return ((MemorySegment) GetConsoleWindow.invokeExact()).address() != 0L;
        } catch (Throwable t) {
            return System.console() != null;
        }
    }

    /**
     * True when stdout is a pipe or a file, meaning something is capturing the answer --
     * a shell adapter, a script, or an agent. Distinguishing this from "no console at all"
     * is what stops a redirected run being mistaken for a double-click.
     */
    public static boolean stdoutCaptured() {
        if (!windows()) return System.console() == null;
        try {
            bind();
            var h = (MemorySegment) GetStdHandle.invokeExact(-11 /* STD_OUTPUT_HANDLE */);
            var type = (int) GetFileType.invokeExact(h);
            return type == 1 /* DISK */ || type == 3 /* PIPE */;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean windows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static synchronized void bind() {
        if (GetConsoleWindow != null) return;
        var lk = Linker.nativeLinker();
        var k32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
        GetConsoleWindow = lk.downcallHandle(k32.find("GetConsoleWindow").orElseThrow(),
                FunctionDescriptor.of(ADDRESS));
        GetStdHandle = lk.downcallHandle(k32.find("GetStdHandle").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT));
        GetFileType = lk.downcallHandle(k32.find("GetFileType").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
    }

    private LaunchMode() {}
}
