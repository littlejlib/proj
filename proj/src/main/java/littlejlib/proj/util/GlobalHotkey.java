package littlejlib.proj.util;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The key that summons the picker from inside whatever program the user is in.
 * <p>
 * WHY {@code RegisterHotKey} AND NOT A KEYBOARD HOOK. The first version of this listened for a
 * double-tapped modifier through a low-level hook, which was wrong in two ways that only show up in
 * use. A hook is PASSIVE - it can observe a key and cannot swallow it - so binding anything Windows
 * already uses fires twice: {@code Win+E} would have opened the picker and File Explorer. And a
 * modifier tapped twice cannot be told apart from a modifier tapped twice for some other reason:
 * Shift five times is the Sticky Keys shortcut, and two summons in a row is already four of them.
 * <p>
 * {@code RegisterHotKey} CLAIMS the combination instead. Windows routes it here and performs no
 * action of its own, so nothing double-fires; there is no timing window and no chord-versus-tap
 * heuristic to get wrong; and a combination that is already spoken for makes the call FAIL, which
 * is a fact reported at startup rather than a key that mysteriously does nothing.
 * <p>
 * It also allows a BARE key with no modifier, which a hook could never do safely - so
 * {@code scrolllock} or {@code menu} are real answers for a user who finds chords painful, and
 * claiming them suppresses whatever they would otherwise have done.
 * <p>
 * The hotkey is posted to a THREAD queue rather than a window, so this needs no window of its own -
 * just a thread sitting in {@code GetMessage}. Everything is best-effort: on a platform without
 * these calls, or when the combination is taken, the tray icon and the command line are unchanged.
 */
public final class GlobalHotkey {

    private GlobalHotkey() { }

    public static final String DEFAULT = "win+\\";

    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    static final int MOD_ALT = 0x1, MOD_CONTROL = 0x2, MOD_SHIFT = 0x4, MOD_WIN = 0x8,
            MOD_NOREPEAT = 0x4000, WM_HOTKEY = 0x0312, WM_QUIT = 0x0012, HOTKEY_ID = 0xC0DE;

    static volatile Thread pump;
    static volatile int pumpThreadId;

    /** Modifier bits and a virtual key code - the two numbers {@code RegisterHotKey} wants. */
    record Combo(int mods, int vk, String label) { }

    /**
     * Claims the combination and starts listening. Returns false when it is off, unsupported here,
     * unparseable, or already claimed by something else - never throws.
     */
    public static boolean install(String spec, Runnable onFire) {
        uninstall();
        var combo = parse(spec);
        if (combo == null || !WINDOWS) return false;

        var started = new java.util.concurrent.CompletableFuture<Boolean>();
        var thread = new Thread(() -> pump(combo, onFire, started), "proj-hotkey");
        thread.setDaemon(true);
        pump = thread;
        thread.start();
        try {
            // Waited on, so the caller can REPORT whether the key was claimed. A summon key that
            // silently failed to register is the one failure mode that looks identical to a user
            // who has simply not pressed it yet.
            return started.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    public static void uninstall() {
        var thread = pump;
        pump = null;
        if (thread == null) return;
        try {
            var api = api();
            if (api != null && pumpThreadId != 0) {
                var posted = (int) api.postThreadMessage.invokeExact(
                        pumpThreadId, WM_QUIT, 0L, 0L);
            }
            thread.join(2000);
        } catch (Throwable ignored) {
            // shutting down anyway
        }
    }

    static void pump(Combo combo, Runnable onFire, java.util.concurrent.CompletableFuture<Boolean> started) {
        var api = api();
        if (api == null) {
            started.complete(false);
            return;
        }
        try (var arena = Arena.ofConfined()) {
            pumpThreadId = (int) api.currentThreadId.invokeExact();
            var ok = (int) api.registerHotKey.invokeExact(
                    MemorySegment.NULL, HOTKEY_ID, combo.mods() | MOD_NOREPEAT, combo.vk()) != 0;
            started.complete(ok);
            if (!ok) return;

            // Over-allocated on purpose: MSG is 48 bytes on x64 and has grown a field before now.
            // Only `message`, at offset 8, is ever read.
            var msg = arena.allocate(64);
            while (pump != null) {
                var got = (int) api.getMessage.invokeExact(msg, MemorySegment.NULL, 0, 0);
                if (got <= 0) break;                       // 0 is WM_QUIT, -1 is an error
                if (msg.get(ValueLayout.JAVA_INT, 8) == WM_HOTKEY) {
                    // Off this thread: it must get straight back to GetMessage, and what it starts
                    // opens a window and blocks until a person closes it.
                    new Thread(onFire, "proj-summon").start();
                }
            }
            var unregistered = (int) api.unregisterHotKey.invokeExact(MemorySegment.NULL, HOTKEY_ID);
        } catch (Throwable t) {
            started.complete(false);
            System.err.println("[proj] the summon key stopped: " + t);
        } finally {
            pumpThreadId = 0;
        }
    }

    // ---------- parsing ----------

    /** How a spec reads back to a person: {@code win+\} becomes {@code Win+\}. */
    public static String describe(String spec) {
        var combo = parse(spec);
        return combo == null ? "off" : combo.label();
    }

    public static boolean isOff(String spec) {
        return parse(spec) == null;
    }

    /**
     * {@code win+\}, {@code ctrl+alt+p}, {@code scrolllock}. Null for "off" or anything unreadable -
     * an unreadable spec is off rather than a guess, because guessing produces a key the user did
     * not ask for and cannot find.
     */
    static Combo parse(String spec) {
        if (spec == null || spec.isBlank() || "off".equalsIgnoreCase(spec.trim())) return null;

        var mods = 0;
        var label = new StringBuilder();
        var vk = -1;
        for (var part : spec.trim().toLowerCase().split("\\+")) {
            var token = part.trim();
            if (token.isEmpty()) {
                // A trailing "+" is the plus KEY, not a separator: "ctrl++" means Ctrl and plus.
                token = "+";
            }
            switch (token) {
                case "win", "windows", "meta", "cmd" -> { mods |= MOD_WIN; label.append("Win+"); }
                case "ctrl", "control" -> { mods |= MOD_CONTROL; label.append("Ctrl+"); }
                case "alt" -> { mods |= MOD_ALT; label.append("Alt+"); }
                case "shift" -> { mods |= MOD_SHIFT; label.append("Shift+"); }
                default -> {
                    var code = keyCode(token);
                    if (code < 0) return null;
                    if (vk >= 0) return null;              // two keys is not a combination we can claim
                    vk = code;
                    label.append(display(token));
                }
            }
        }
        return vk < 0 ? null : new Combo(mods, vk, label.toString());
    }

    static final Map<String, Integer> KEYS = new LinkedHashMap<>();

    static {
        // The oddments, by their printed character. A user writing this into a settings file writes
        // the key they can see on the keycap, not VK_OEM_5.
        KEYS.put("\\", 0xDC);
        KEYS.put("`", 0xC0);
        KEYS.put("[", 0xDB);
        KEYS.put("]", 0xDD);
        KEYS.put(";", 0xBA);
        KEYS.put("'", 0xDE);
        KEYS.put(",", 0xBC);
        KEYS.put(".", 0xBE);
        KEYS.put("/", 0xBF);
        KEYS.put("-", 0xBD);
        KEYS.put("=", 0xBB);
        KEYS.put("+", 0xBB);
        KEYS.put("space", 0x20);
        KEYS.put("tab", 0x09);
        KEYS.put("insert", 0x2D);
        KEYS.put("scrolllock", 0x91);
        KEYS.put("pause", 0x13);
        KEYS.put("menu", 0x5D);
        KEYS.put("apps", 0x5D);
    }

    static int keyCode(String token) {
        var known = KEYS.get(token);
        if (known != null) return known;
        if (token.length() == 1) {
            var c = token.charAt(0);
            if (c >= 'a' && c <= 'z') return c - 'a' + 0x41;
            if (c >= '0' && c <= '9') return c - '0' + 0x30;
        }
        if (token.matches("f([1-9]|1\\d|2[0-4])")) {
            return 0x70 + Integer.parseInt(token.substring(1)) - 1;
        }
        return -1;
    }

    static String display(String token) {
        if (token.length() == 1) return token.toUpperCase();
        return Character.toUpperCase(token.charAt(0)) + token.substring(1);
    }

    // ---------- win32 ----------

    static volatile Api api;
    static volatile boolean tried;

    static Api api() {
        if (!tried) {
            synchronized (GlobalHotkey.class) {
                if (!tried) {
                    tried = true;
                    try {
                        api = WINDOWS ? new Api() : null;
                    } catch (Throwable t) {
                        System.err.println("[proj] no win32 hotkey support here: " + t);
                        api = null;
                    }
                }
            }
        }
        return api;
    }

    static final class Api {
        final MethodHandle registerHotKey, unregisterHotKey, getMessage, postThreadMessage,
                currentThreadId;

        Api() {
            var linker = Linker.nativeLinker();
            var user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            var kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());

            var ptr = ValueLayout.ADDRESS;
            var i32 = ValueLayout.JAVA_INT;
            var i64 = ValueLayout.JAVA_LONG;

            registerHotKey = linker.downcallHandle(user32.find("RegisterHotKey").orElseThrow(),
                    FunctionDescriptor.of(i32, ptr, i32, i32, i32));
            unregisterHotKey = linker.downcallHandle(user32.find("UnregisterHotKey").orElseThrow(),
                    FunctionDescriptor.of(i32, ptr, i32));
            getMessage = linker.downcallHandle(user32.find("GetMessageW").orElseThrow(),
                    FunctionDescriptor.of(i32, ptr, ptr, i32, i32));
            postThreadMessage = linker.downcallHandle(user32.find("PostThreadMessageW").orElseThrow(),
                    FunctionDescriptor.of(i32, i32, i32, i64, i64));
            currentThreadId = linker.downcallHandle(kernel32.find("GetCurrentThreadId").orElseThrow(),
                    FunctionDescriptor.of(i32));
        }
    }
}
