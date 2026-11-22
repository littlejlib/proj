package littlejlib.proj.util;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/** The Win32 calls the tray needs, as downcall handles. Each distinct signature is also a line in the native-image metadata. */
final class Win32 {
    private Win32() { }

    static final Linker LINKER = Linker.nativeLinker();
    static final SymbolLookup
            USER32 = SymbolLookup.libraryLookup("user32", Arena.global()),
            KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global()),
            SHELL32 = SymbolLookup.libraryLookup("shell32", Arena.global());

    static MethodHandle fn(SymbolLookup lib, String name, FunctionDescriptor d) {
        return LINKER.downcallHandle(lib.find(name).orElseThrow(), d);
    }

    static final MethodHandle
            getModuleHandle = fn(KERNEL32, "GetModuleHandleW", FunctionDescriptor.of(ADDRESS, ADDRESS)),
            registerClass = fn(USER32, "RegisterClassExW", FunctionDescriptor.of(JAVA_SHORT, ADDRESS)),
            createWindow = fn(USER32, "CreateWindowExW", FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS, ADDRESS,
                    JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS)),
            defWindowProc = fn(USER32, "DefWindowProcW", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG)),
            getMessage = fn(USER32, "GetMessageW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT)),
            translateMessage = fn(USER32, "TranslateMessage", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            dispatchMessage = fn(USER32, "DispatchMessageW", FunctionDescriptor.of(JAVA_LONG, ADDRESS)),
            postMessage = fn(USER32, "PostMessageW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG)),
            postQuit = fn(USER32, "PostQuitMessage", FunctionDescriptor.ofVoid(JAVA_INT)),
            destroyWindow = fn(USER32, "DestroyWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            createPopupMenu = fn(USER32, "CreatePopupMenu", FunctionDescriptor.of(ADDRESS)),
            appendMenu = fn(USER32, "AppendMenuW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS)),
            trackPopupMenu = fn(USER32, "TrackPopupMenu", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT,
                    JAVA_INT, JAVA_INT, ADDRESS, ADDRESS)),
            destroyMenu = fn(USER32, "DestroyMenu", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            getCursorPos = fn(USER32, "GetCursorPos", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            setForeground = fn(USER32, "SetForegroundWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            registerMessage = fn(USER32, "RegisterWindowMessageW", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            systemMetric = fn(USER32, "GetSystemMetrics", FunctionDescriptor.of(JAVA_INT, JAVA_INT)),
            createIcon = fn(USER32, "CreateIconFromResourceEx", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT,
                    JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT)),
            destroyIcon = fn(USER32, "DestroyIcon", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            notifyIcon = fn(SHELL32, "Shell_NotifyIconW", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
}
