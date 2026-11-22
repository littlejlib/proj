package littlejlib.proj.terminal;

import littlejlib.proj.LaunchMode;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.*;

/**
 * Typing a command into the shell that is ALREADY in this tab, by writing key events into the
 * console's own input buffer.
 * <p>
 * The alternative - running the agent as a child of this JVM with inherited stdio - puts Java
 * between claude-code and the console for the whole life of the session, and claude-code's TUI
 * does not survive that: bracketed paste stops working, which is how the user found it. Nothing
 * about the arrangement is fixable from here, because the problem IS the arrangement.
 * <p>
 * So proj stops trying to be the parent. It queues the command line, returns, and exits; the shell
 * that was blocked waiting for proj reads what is queued and runs it. claude-code is then a direct
 * child of cmd.exe with the console entirely to itself - the same process tree it would have had if
 * the command had been typed by hand, which is the only arrangement known to work.
 * <p>
 * This is also why the console API is right and Tabby's plugin is not: {@code WriteConsoleInput}
 * needs no plugin, no port and no way to tell which tab is ours, and it works in Windows Terminal
 * and conhost too.
 */
public final class ConsoleInput {

    private ConsoleInput() { }

    static final int GENERIC_READ = 0x8000_0000, GENERIC_WRITE = 0x4000_0000;
    static final int FILE_SHARE_READ = 1, FILE_SHARE_WRITE = 2;
    static final int OPEN_EXISTING = 3;
    static final int KEY_EVENT = 1, VK_RETURN = 0x0D, KEY_ATTEMPTS = 64;

    /** WORD EventType, then KEY_EVENT_RECORD on a 4-byte boundary: 20 bytes, x86 and x64 alike. */
    static final int RECORD = 20;

    static MethodHandle CreateFileW, WriteConsoleInputW, CloseHandle, GetNumberOfConsoleInputEvents,
            ReadConsoleInputW;

    /**
     * One keypress, as its character, or -1. Blocks.
     * <p>
     * This is the other half of the same struct knowledge the write side needs, which is why it
     * lives here rather than in a second class. It replaces JLine's raw mode - the only thing three
     * separate menus were using a whole terminal library for.
     * <p>
     * It DOES read the console - the hazard that JLine used to create permanently - but only
     * while someone is being prompted, and never for longer than the keypress. Nothing is queued
     * while a person is answering a question.
     */
    public static int readKey() {
        if (!windows()) return -1;
        try {
            bind();
        } catch (Throwable t) {
            return -1;
        }
        try (var arena = Arena.ofConfined()) {
            var name = arena.allocateFrom("CONIN$", StandardCharsets.UTF_16LE);
            var handle = (MemorySegment) CreateFileW.invokeExact(
                    name, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE,
                    MemorySegment.NULL, OPEN_EXISTING, 0, MemorySegment.NULL);
            if (handle.address() == 0L || handle.address() == -1L) return -1;
            try {
                var record = arena.allocate(RECORD);
                var read = arena.allocate(JAVA_INT);
                // Key UP events and modifier presses carry no character; keep reading past them.
                for (var attempt = 0; attempt < KEY_ATTEMPTS; attempt++) {
                    var ok = (int) ReadConsoleInputW.invokeExact(handle, record, 1, read);
                    if (ok == 0 || read.get(JAVA_INT, 0) != 1) return -1;
                    if (record.get(JAVA_SHORT, 0) != KEY_EVENT) continue;
                    if (record.get(JAVA_INT, 4) == 0) continue;          // bKeyDown
                    var ch = record.get(JAVA_SHORT, 14) & 0xFFFF;        // uChar.UnicodeChar
                    if (ch != 0) return ch;
                }
                return -1;
            } finally {
                var ignored = (int) CloseHandle.invokeExact(handle);
            }
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * How many records are sitting in the queue, or -1. Diagnostic: a count that FALLS while this
     * process is still alive means something in this process is reading console input, which is the
     * bug that made a queued command line arrive with its first 24 characters missing.
     */
    public static int pending() {
        if (!windows()) return -1;
        try {
            bind();
        } catch (Throwable t) {
            return -1;
        }
        try (var arena = Arena.ofConfined()) {
            var name = arena.allocateFrom("CONIN$", StandardCharsets.UTF_16LE);
            var handle = (MemorySegment) CreateFileW.invokeExact(
                    name, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE,
                    MemorySegment.NULL, OPEN_EXISTING, 0, MemorySegment.NULL);
            if (handle.address() == 0L || handle.address() == -1L) return -1;
            try {
                var count = arena.allocate(JAVA_INT);
                var ok = (int) GetNumberOfConsoleInputEvents.invokeExact(handle, count);
                return ok == 0 ? -1 : count.get(JAVA_INT, 0);
            } finally {
                var ignored = (int) CloseHandle.invokeExact(handle);
            }
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * A console to type into, and nothing capturing what we write. The second half matters: a shell
     * adapter or a script reading stdout is not a person watching a terminal, and a command queued
     * into a console nobody is reading is a command that never runs.
     */
    public static boolean available() {
        return LaunchMode.hasConsole() && !LaunchMode.stdoutCaptured() && windows();
    }

    /**
     * Queues {@code command} and a Return. True means the records were accepted by the console, not
     * that the command succeeded - by design, since it does not run until this process is gone.
     */
    public static boolean type(String command) {
        if (!windows()) return false;
        try {
            bind();
        } catch (Throwable t) {
            return false;
        }
        try (var arena = Arena.ofConfined()) {
            var name = arena.allocateFrom("CONIN$", StandardCharsets.UTF_16LE);
            var handle = (MemorySegment) CreateFileW.invokeExact(
                    name, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE,
                    MemorySegment.NULL, OPEN_EXISTING, 0, MemorySegment.NULL);
            if (handle.address() == 0L || handle.address() == -1L) return false;
            try {
                return write(arena, handle, command + "\r");
            } finally {
                var ignored = (int) CloseHandle.invokeExact(handle);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** Each character as a key down and a key up, which is what a keyboard would have produced. */
    static boolean write(Arena arena, MemorySegment handle, String text) throws Throwable {
        var records = arena.allocate((long) RECORD * text.length() * 2);
        var at = 0L;
        for (var i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);
            var vk = ch == '\r' ? VK_RETURN : 0;
            at = keyEvent(records, at, ch, vk, true);
            at = keyEvent(records, at, ch, vk, false);
        }
        var written = arena.allocate(JAVA_INT);
        var ok = (int) WriteConsoleInputW.invokeExact(handle, records, text.length() * 2, written);
        return ok != 0 && written.get(JAVA_INT, 0) == text.length() * 2;
    }

    static long keyEvent(MemorySegment records, long at, char ch, int vk, boolean down) {
        records.set(JAVA_SHORT, at, (short) KEY_EVENT);
        records.set(JAVA_INT, at + 4, down ? 1 : 0);      // bKeyDown
        records.set(JAVA_SHORT, at + 8, (short) 1);        // wRepeatCount
        records.set(JAVA_SHORT, at + 10, (short) vk);      // wVirtualKeyCode
        records.set(JAVA_SHORT, at + 12, (short) 0);       // wVirtualScanCode
        records.set(JAVA_SHORT, at + 14, (short) ch);      // uChar.UnicodeChar
        records.set(JAVA_INT, at + 16, 0);                 // dwControlKeyState
        return at + RECORD;
    }

    static boolean windows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static synchronized void bind() {
        if (CreateFileW != null) return;
        var lk = Linker.nativeLinker();
        var k32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
        CreateFileW = lk.downcallHandle(k32.find("CreateFileW").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS,
                        JAVA_INT, JAVA_INT, ADDRESS));
        WriteConsoleInputW = lk.downcallHandle(k32.find("WriteConsoleInputW").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        CloseHandle = lk.downcallHandle(k32.find("CloseHandle").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
        ReadConsoleInputW = lk.downcallHandle(k32.find("ReadConsoleInputW").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        GetNumberOfConsoleInputEvents = lk.downcallHandle(
                k32.find("GetNumberOfConsoleInputEvents").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    }
}
