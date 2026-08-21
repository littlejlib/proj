package littlejlib.proj.util;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Takes the Windows foreground for a window summoned by the global key.
 * <p>
 * SHOWING A WINDOW AND ASKING FOR FOCUS IS NOT ENOUGH, and that is the whole reason this exists.
 * Windows refuses {@code SetForegroundWindow} unless the calling process received the last input
 * event - a deliberate anti-focus-stealing rule. A window summoned by a global hotkey never
 * qualifies: the tap is observed through a low-level hook, which does not count as this process
 * receiving it. So the window appears and every keystroke keeps going to whatever the user was in,
 * which for a keyboard-first picker means it may as well not have opened.
 * <p>
 * The documented way through is to attach this thread's input queue to the foreground window's
 * thread first. While attached the two share a focus state, so the call is permitted; it is
 * detached again immediately, because leaving it attached couples two applications' input handling
 * for as long as they both live.
 * <p>
 * FFM rather than JNA, so this adds no dependency - the launcher already passes
 * {@code --enable-native-access}. Everything is best-effort and returns a boolean rather than
 * throwing: on a machine where the call is refused, JavaFX's own {@code toFront} still runs and the
 * window is no worse off than before. A failure to steal focus must never be a failure to open.
 */
public final class WindowsForeground {

    private WindowsForeground() { }

    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    static final int SW_RESTORE = 9;

    static volatile Api api;
    static volatile boolean tried;

    /**
     * Brings the window with this exact title to the front and gives it keyboard focus.
     *
     * @return false if there is no such window, or the platform refused - never throws
     */
    public static boolean grab(String windowTitle) {
        if (!WINDOWS || windowTitle == null) return false;
        var a = api();
        if (a == null) return false;
        try (var arena = Arena.ofConfined()) {
            var title = arena.allocateFrom(windowTitle, StandardCharsets.UTF_16LE);
            var hwnd = (MemorySegment) a.findWindow.invokeExact(MemorySegment.NULL, title);
            if (hwnd == null || hwnd.address() == 0) return false;

            var mine = (int) a.currentThreadId.invokeExact();
            var foreground = (MemorySegment) a.getForegroundWindow.invokeExact();
            var theirs = foreground.address() == 0 ? 0
                    : (int) a.windowThreadProcessId.invokeExact(foreground, MemorySegment.NULL);

            // Attaching a thread to itself fails, and would be pointless: already foreground means
            // there is nobody to ask permission from.
            var attach = theirs != 0 && theirs != mine;
            var attached = attach && (int) a.attachThreadInput.invokeExact(theirs, mine, 1) != 0;
            try {
                var ignoredShow = (int) a.showWindow.invokeExact(hwnd, SW_RESTORE);
                var ok = (int) a.setForegroundWindow.invokeExact(hwnd) != 0;
                var ignoredFocus = (MemorySegment) a.setFocus.invokeExact(hwnd);
                return ok;
            } finally {
                if (attached) {
                    var ignoredDetach = (int) a.attachThreadInput.invokeExact(theirs, mine, 0);
                }
            }
        } catch (Throwable t) {
            return false;
        }
    }

    static Api api() {
        if (!tried) {
            synchronized (WindowsForeground.class) {
                if (!tried) {
                    tried = true;
                    try {
                        api = new Api();
                    } catch (Throwable t) {
                        System.err.println("[proj] no win32 foreground support here: " + t);
                        api = null;
                    }
                }
            }
        }
        return api;
    }

    /**
     * The handles, resolved once. Held in a class rather than eight static fields so that a failure
     * to resolve any one of them leaves the whole thing null instead of half-usable.
     */
    static final class Api {
        final MethodHandle findWindow, getForegroundWindow, windowThreadProcessId,
                attachThreadInput, showWindow, setForegroundWindow, setFocus, currentThreadId;

        Api() {
            var linker = Linker.nativeLinker();
            // Global arena: these libraries stay loaded for the life of the process anyway, and a
            // confined one would unload them under the handles.
            var user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            var kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());

            var ptr = ValueLayout.ADDRESS;
            var i32 = ValueLayout.JAVA_INT;

            findWindow = handle(linker, user32, "FindWindowW", FunctionDescriptor.of(ptr, ptr, ptr));
            getForegroundWindow = handle(linker, user32, "GetForegroundWindow", FunctionDescriptor.of(ptr));
            windowThreadProcessId = handle(linker, user32, "GetWindowThreadProcessId",
                    FunctionDescriptor.of(i32, ptr, ptr));
            attachThreadInput = handle(linker, user32, "AttachThreadInput",
                    FunctionDescriptor.of(i32, i32, i32, i32));
            showWindow = handle(linker, user32, "ShowWindow", FunctionDescriptor.of(i32, ptr, i32));
            setForegroundWindow = handle(linker, user32, "SetForegroundWindow",
                    FunctionDescriptor.of(i32, ptr));
            setFocus = handle(linker, user32, "SetFocus", FunctionDescriptor.of(ptr, ptr));
            currentThreadId = handle(linker, kernel32, "GetCurrentThreadId", FunctionDescriptor.of(i32));
        }

        static MethodHandle handle(Linker linker, SymbolLookup lookup, String name, FunctionDescriptor fd) {
            return linker.downcallHandle(lookup.find(name).orElseThrow(
                    () -> new IllegalStateException("win32 symbol not found: " + name)), fd);
        }
    }
}
