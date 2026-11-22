package littlejlib.proj.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static java.lang.foreign.ValueLayout.*;

/**
 * A notification-area icon with a right-click menu, on plain Win32 through FFM, so it needs neither AWT
 * (which a native image cannot carry as one exe) nor anything else. One thread owns the hidden window and its
 * message loop; menu actions run on threads of their own so the loop never waits on them.
 * <p>
 * Labels of the menu are text, "-" is a separator. A double-click on the icon runs {@code onClick}.
 */
public final class WinTray {
    static final int APP = 0x8001, DBLCLK = 0x203, RBUTTONUP = 0x205, DESTROY = 2, NIM_ADD = 0, NIM_MODIFY = 1, NIM_DELETE = 2,
            NIF_MESSAGE = 1, NIF_ICON = 2, NIF_TIP = 4, NIF_INFO = 0x10, NOTIFYICONDATA = 976, TIP = 40, INFO = 304, INFO_TITLE = 820;
    static final String CLASS = "projTrayWindow";

    final String tip;
    final IntFunction<InputStream> icons;
    final Runnable onClick;
    final List<String> labels;
    final List<Runnable> actions;
    final Arena arena = Arena.ofShared();
    final MemorySegment data = arena.allocate(NOTIFYICONDATA);
    volatile MemorySegment window = MemorySegment.NULL;
    int taskbarCreated;

    /** {@code icons} gives the PNG of the mark at the smallest drawn size of at least the asked pixels. */
    public WinTray(String tip, IntFunction<InputStream> icons, Runnable onClick, List<String> labels, List<Runnable> actions) {
        this.tip = tip;
        this.icons = icons;
        this.onClick = onClick;
        this.labels = labels;
        this.actions = actions;
    }

    public boolean start() {
        var ready = new CompletableFuture<Boolean>();
        var thread = new Thread(() -> loop(ready), "proj-tray");
        thread.setDaemon(true);
        thread.start();
        try {
            return ready.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    public void tooltip(String text) {
        if (window.equals(MemorySegment.NULL)) return;
        text(TIP, 128, text);
        notify(NIM_MODIFY, NIF_TIP);
    }

    public void balloon(String title, String body) {
        if (window.equals(MemorySegment.NULL)) return;
        text(INFO_TITLE, 64, title);
        text(INFO, 256, body);
        notify(NIM_MODIFY, NIF_INFO);
    }

    public void stop() {
        var w = window;
        if (w.equals(MemorySegment.NULL)) return;
        window = MemorySegment.NULL;
        try {
            var posted = (int) Win32.postMessage.invokeExact(w, 0x10, 0L, 0L);
        } catch (Throwable ignored) {
        }
    }

    void loop(CompletableFuture<Boolean> ready) {
        try (var local = Arena.ofConfined()) {
            var hwnd = create(local);
            window = hwnd;
            taskbarCreated = (int) Win32.registerMessage.invokeExact(local.allocateFrom("TaskbarCreated", StandardCharsets.UTF_16LE));
            data.set(JAVA_INT, 0, NOTIFYICONDATA);
            data.set(ADDRESS, 8, hwnd);
            data.set(JAVA_INT, 16, 1);
            data.set(JAVA_INT, 24, APP);
            data.set(ADDRESS, 32, icon(local));
            text(TIP, 128, tip);
            ready.complete(notify(NIM_ADD, NIF_MESSAGE | NIF_ICON | NIF_TIP));
            var msg = local.allocate(64);
            while ((int) Win32.getMessage.invokeExact(msg, MemorySegment.NULL, 0, 0) > 0) {
                var t = (int) Win32.translateMessage.invokeExact(msg);
                var d = (long) Win32.dispatchMessage.invokeExact(msg);
            }
        } catch (Throwable t) {
            ready.complete(false);
            System.err.println("[proj] tray stopped: " + t);
            t.printStackTrace();
        }
    }

    MemorySegment create(Arena local) throws Throwable {
        var module = (MemorySegment) Win32.getModuleHandle.invokeExact(MemorySegment.NULL);
        var proc = Win32.LINKER.upcallStub(MethodHandles.lookup().findVirtual(WinTray.class, "proc",
                        MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class)).bindTo(this),
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG), arena);
        var name = local.allocateFrom(CLASS, StandardCharsets.UTF_16LE);
        var wc = local.allocate(80);
        wc.set(JAVA_INT, 0, 80);
        wc.set(ADDRESS, 8, proc);
        wc.set(ADDRESS, 24, module);
        wc.set(ADDRESS, 64, name);
        var atom = (short) Win32.registerClass.invokeExact(wc);
        return (MemorySegment) Win32.createWindow.invokeExact(0, name, name, 0, 0, 0, 0, 0,
                MemorySegment.ofAddress(-3L), MemorySegment.NULL, module, MemorySegment.NULL);
    }

    MemorySegment icon(Arena local) throws Throwable {
        var px = (int) Win32.systemMetric.invokeExact(49);
        try (var in = icons.apply(px)) {
            var png = in.readAllBytes();
            return (MemorySegment) Win32.createIcon.invokeExact(local.allocateFrom(JAVA_BYTE, png), png.length, 1, 0x00030000, px, px, 0);
        } catch (IOException e) {
            return MemorySegment.NULL;
        }
    }

    long proc(MemorySegment hwnd, int msg, long w, long l) {
        try {
            if (msg == APP) {
                var ev = (int) l & 0xFFFF;
                if (ev == DBLCLK) new Thread(onClick, "proj-tray-click").start();
                else if (ev == RBUTTONUP) menu(hwnd);
                return 0;
            }
            if (msg == taskbarCreated && taskbarCreated != 0) {
                notify(NIM_ADD, NIF_MESSAGE | NIF_ICON | NIF_TIP);
                return 0;
            }
            if (msg == DESTROY) {
                notify(NIM_DELETE, 0);
                Win32.postQuit.invokeExact(0);
                return 0;
            }
            return (long) Win32.defWindowProc.invokeExact(hwnd, msg, w, l);
        } catch (Throwable t) {
            return 0;
        }
    }

    void menu(MemorySegment hwnd) throws Throwable {
        try (var local = Arena.ofConfined()) {
            var popup = (MemorySegment) Win32.createPopupMenu.invokeExact();
            for (var i = 0; i < labels.size(); i++) {
                var sep = labels.get(i).equals("-");
                var added = (int) Win32.appendMenu.invokeExact(popup, sep ? 0x800 : 0, (long) i + 1,
                        sep ? MemorySegment.NULL : local.allocateFrom(labels.get(i), StandardCharsets.UTF_16LE));
            }
            var pt = local.allocate(8);
            var got = (int) Win32.getCursorPos.invokeExact(pt);
            var fg = (int) Win32.setForeground.invokeExact(hwnd);
            var chosen = (int) Win32.trackPopupMenu.invokeExact(popup, 0x102, pt.get(JAVA_INT, 0), pt.get(JAVA_INT, 4), 0, hwnd, MemorySegment.NULL);
            var gone = (int) Win32.destroyMenu.invokeExact(popup);
            if (chosen > 0) new Thread(actions.get(chosen - 1), "proj-tray-action").start();
        }
    }

    boolean notify(int what, int flags) {
        try {
            data.set(JAVA_INT, 20, flags);
            return (int) Win32.notifyIcon.invokeExact(what, data) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    void text(long offset, int chars, String s) {
        var bytes = (s.length() > chars - 1 ? s.substring(0, chars - 1) : s).getBytes(StandardCharsets.UTF_16LE);
        MemorySegment.copy(MemorySegment.ofArray(bytes), 0, data, offset, bytes.length);
        data.set(JAVA_SHORT, offset + bytes.length, (short) 0);
    }
}
