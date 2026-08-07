package littlejlib.proj;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * Win32 CF_UNICODETEXT clipboard via FFM. No AWT, so no toolkit startup cost on a
 * tool whose selling point is being quick, and no headless trouble.
 *
 * Handles are bound lazily rather than in a static initialiser: this class must
 * stay loadable on the macOS build, where kernel32/user32 do not exist.
 */
public final class Clipboard {

    static MethodHandle GlobalAlloc, GlobalLock, GlobalUnlock,
            OpenClipboard, EmptyClipboard, SetClipboardData, CloseClipboard;

    public static boolean copy(String text) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return false;
        try {
            bind();
            return put(text);
        } catch (Throwable t) {
            return false;
        }
    }

    static synchronized void bind() {
        if (OpenClipboard != null) return;
        var lk = Linker.nativeLinker();
        var k32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
        var u32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
        GlobalAlloc = h(lk, k32, "GlobalAlloc", ADDRESS, JAVA_INT, JAVA_LONG);
        GlobalLock = h(lk, k32, "GlobalLock", ADDRESS, ADDRESS);
        GlobalUnlock = h(lk, k32, "GlobalUnlock", JAVA_INT, ADDRESS);
        EmptyClipboard = h(lk, u32, "EmptyClipboard", JAVA_INT);
        OpenClipboard = h(lk, u32, "OpenClipboard", JAVA_INT, ADDRESS);
        SetClipboardData = h(lk, u32, "SetClipboardData", ADDRESS, JAVA_INT, ADDRESS);
        CloseClipboard = h(lk, u32, "CloseClipboard", JAVA_INT);
    }

    static MethodHandle h(Linker lk, SymbolLookup lib, String name, MemoryLayout ret, MemoryLayout... args) {
        return lk.downcallHandle(lib.find(name).orElseThrow(() -> new IllegalStateException(name)),
                FunctionDescriptor.of(ret, args));
    }

    static boolean put(String text) throws Throwable {
        if ((int) OpenClipboard.invokeExact(MemorySegment.NULL) == 0) return false;
        try {
            var _ = (int) EmptyClipboard.invokeExact();
            var bytes = (text.length() + 1) * 2L;
            var mem = (MemorySegment) GlobalAlloc.invokeExact(2 /* GMEM_MOVEABLE */, bytes);
            if (mem.address() == 0L) return false;
            var p = ((MemorySegment) GlobalLock.invokeExact(mem)).reinterpret(bytes);
            for (var i = 0; i < text.length(); i++) p.set(JAVA_CHAR, i * 2L, text.charAt(i));
            p.set(JAVA_CHAR, text.length() * 2L, '\0');
            var _ = (int) GlobalUnlock.invokeExact(mem);
            // On success the system owns mem; it must not be freed here.
            return ((MemorySegment) SetClipboardData.invokeExact(13 /* CF_UNICODETEXT */, mem)).address() != 0L;
        } finally {
            var _ = (int) CloseClipboard.invokeExact();
        }
    }

    private Clipboard() {}
}
