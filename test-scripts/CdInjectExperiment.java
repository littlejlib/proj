///usr/bin/env java "$0" "$@" ; exit $?
// Standalone Java code (not part of main project) - replaces bash/python/batch scripts with IDE-friendly, maintainable code using JDK 11/21/25 enhancements. To know why, refer to Cay Horstmann's JavaOne 2025 talk "Java for Small Coding Tasks" (https://youtu.be/04wFgshWMdA)

import module java.base;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

public class CdInjectExperiment {

    static final Linker LK = Linker.nativeLinker();
    static final Arena G = Arena.global();
    static final SymbolLookup K32 = SymbolLookup.libraryLookup("kernel32.dll", G);
    static final SymbolLookup U32 = SymbolLookup.libraryLookup("user32.dll", G);
    static final StructLayout CAP = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERR = CAP.varHandle(groupElement("GetLastError"));

    static MethodHandle f(SymbolLookup l, String n, MemoryLayout ret, MemoryLayout... a) {
        return LK.downcallHandle(l.find(n).orElseThrow(() -> new IllegalStateException(n)),
                FunctionDescriptor.of(ret, a));
    }

    static MethodHandle fc(SymbolLookup l, String n, MemoryLayout ret, MemoryLayout... a) {
        return LK.downcallHandle(l.find(n).orElseThrow(() -> new IllegalStateException(n)),
                FunctionDescriptor.of(ret, a), Linker.Option.captureCallState("GetLastError"));
    }

    static final MethodHandle
        CreateFileW       = fc(K32, "CreateFileW", ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS),
        WriteConsoleInput = fc(K32, "WriteConsoleInputW", JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS),
        GetStdHandle      = f(K32, "GetStdHandle", ADDRESS, JAVA_INT),
        GetFileType       = f(K32, "GetFileType", JAVA_INT, ADDRESS),
        GetConsoleMode    = f(K32, "GetConsoleMode", JAVA_INT, ADDRESS, ADDRESS),
        CloseHandle       = f(K32, "CloseHandle", JAVA_INT, ADDRESS),
        GlobalAlloc       = f(K32, "GlobalAlloc", ADDRESS, JAVA_INT, JAVA_LONG),
        GlobalLock        = f(K32, "GlobalLock", ADDRESS, ADDRESS),
        GlobalUnlock      = f(K32, "GlobalUnlock", JAVA_INT, ADDRESS),
        GetConsoleWindow  = f(K32, "GetConsoleWindow", ADDRESS),
        OpenClipboard     = f(U32, "OpenClipboard", JAVA_INT, ADDRESS),
        EmptyClipboard    = f(U32, "EmptyClipboard", JAVA_INT),
        SetClipboardData  = f(U32, "SetClipboardData", ADDRESS, JAVA_INT, ADDRESS),
        CloseClipboard    = f(U32, "CloseClipboard", JAVA_INT);

    static final int REC = 20, KEY_EVENT = 1, VK_RETURN = 0x0D, CF_UNICODETEXT = 13,
        GENERIC_RW = 0x80000000 | 0x40000000, SHARE_RW = 3, OPEN_EXISTING = 3,
        STD_IN = -10, STD_OUT = -11, GMEM_MOVEABLE = 2;

    static boolean force = false;

    public static void main(String[] args) throws Throwable {
        var target = args.length > 0 && !args[0].startsWith("--") ? args[0] : System.getProperty("user.dir");
        var opts = List.of(args);
        var mode = opt(opts, "--mode", "auto");
        var enter = !opts.contains("--no-enter");
        var keyup = opts.contains("--keyup");
        force = opts.contains("--force");

        var dir = Path.of(target).toAbsolutePath().normalize().toString();
        diag();
        var cmd = cdFor(shell(), dir);
        err("target command : " + cmd);
        err("mode=" + mode + " enter=" + enter + " keyup=" + keyup);
        err("");

        var done = switch (mode) {
            case "inject"    -> inject(cmd, enter, keyup);
            case "clipboard" -> clipboard(cmd);
            case "stdout"    -> { out(dir); yield true; }
            default          -> inject(cmd, enter, keyup) || clipboard(cmd) || outFallback(dir);
        };
        err(done ? "RESULT: handled" : "RESULT: all tiers failed");
    }

    static boolean inject(String cmd, boolean enter, boolean keyup) throws Throwable {
        try (var a = Arena.ofConfined()) {
            var cap = a.allocate(CAP);
            var h = (MemorySegment) CreateFileW.invokeExact(cap, a.allocateFrom("CONIN$", java.nio.charset.StandardCharsets.UTF_16LE),
                    GENERIC_RW, SHARE_RW, MemorySegment.NULL, OPEN_EXISTING, 0, MemorySegment.NULL);
            if (h.address() == -1L || h.address() == 0L) {
                err("inject: cannot open CONIN$ (err=" + err(cap) + ") -- no real console attached");
                return false;
            }
            var mode = a.allocate(JAVA_INT);
            var isConsole = (int) GetConsoleMode.invokeExact(h, mode) != 0;
            var hasWindow = ((MemorySegment) GetConsoleWindow.invokeExact()).address() != 0L;
            var sh = shell();
            var outIsConsole = !consoleMode(STD_OUT).equals("n/a");
            err("inject signals : CONIN$console=" + isConsole + " stdoutConsole=" + outIsConsole
                    + " consoleWindow=" + hasWindow + " shell=" + sh);
            if (!(isConsole && outIsConsole && !sh.equals("unknown")) && !force) {
                err("inject: REFUSING -- not an interactive shell at a real console (injection would silently vanish).");
                var _ = (int) CloseHandle.invokeExact(h);
                return false;
            }
            var text = enter ? cmd + "\r" : cmd;
            var n = text.length() * (keyup ? 2 : 1);
            var buf = a.allocate((long) n * REC, 4);
            var i = 0;
            for (var ch : text.toCharArray()) {
                var vk = (short) (ch == '\r' ? VK_RETURN : 0);
                putKey(buf, i++, ch, vk, true);
                if (keyup) putKey(buf, i++, ch, vk, false);
            }
            var written = a.allocate(JAVA_INT);
            var ok = (int) WriteConsoleInput.invokeExact(cap, h, buf, n, written);
            var _ = (int) CloseHandle.invokeExact(h);
            if (ok == 0) {
                err("inject: WriteConsoleInputW failed (err=" + err(cap) + ")");
                return false;
            }
            err("inject: wrote " + written.get(JAVA_INT, 0) + "/" + n + " console input records");
            return true;
        }
    }

    static void putKey(MemorySegment b, int i, char ch, short vk, boolean down) {
        var o = i * (long) REC;
        b.set(JAVA_SHORT, o, (short) KEY_EVENT);
        b.set(JAVA_INT, o + 4, down ? 1 : 0);
        b.set(JAVA_SHORT, o + 8, (short) 1);
        b.set(JAVA_SHORT, o + 10, vk);
        b.set(JAVA_SHORT, o + 12, (short) 0);
        b.set(JAVA_CHAR, o + 14, ch);
        b.set(JAVA_INT, o + 16, 0);
    }

    static boolean clipboard(String text) throws Throwable {
        if ((int) OpenClipboard.invokeExact(MemorySegment.NULL) == 0) {
            err("clipboard: OpenClipboard failed (another app owns it)");
            return false;
        }
        try {
            var _ = (int) EmptyClipboard.invokeExact();
            var bytes = (text.length() + 1) * 2L;
            var hMem = (MemorySegment) GlobalAlloc.invokeExact(GMEM_MOVEABLE, bytes);
            if (hMem.address() == 0L) { err("clipboard: GlobalAlloc failed"); return false; }
            var p = ((MemorySegment) GlobalLock.invokeExact(hMem)).reinterpret(bytes);
            for (var i = 0; i < text.length(); i++) p.set(JAVA_CHAR, i * 2L, text.charAt(i));
            p.set(JAVA_CHAR, text.length() * 2L, '\0');
            var _ = (int) GlobalUnlock.invokeExact(hMem);
            var r = (MemorySegment) SetClipboardData.invokeExact(CF_UNICODETEXT, hMem);
            if (r.address() == 0L) { err("clipboard: SetClipboardData failed"); return false; }
            err("clipboard: copied -- paste it into your shell (right-click, or Shift+Insert)");
            return true;
        } finally { var _ = (int) CloseClipboard.invokeExact(); }
    }

    static boolean outFallback(String cmd) { out(cmd); err("stdout: printed command (no console, no clipboard)"); return true; }

    static void diag() throws Throwable {
        var cw = (MemorySegment) GetConsoleWindow.invokeExact();
        err("os.name        : " + System.getProperty("os.name"));
        err("GetConsoleWindow: 0x" + Long.toHexString(cw.address()) + (cw.address() == 0 ? "  (NO console)" : ""));
        err("stdin  type    : " + fileType(STD_IN) + "   console-mode=" + consoleMode(STD_IN));
        err("stdout type    : " + fileType(STD_OUT) + "   console-mode=" + consoleMode(STD_OUT));
        err("System.console(): " + (System.console() != null));
        var p = ProcessHandle.current().parent();
        for (var lvl = 1; p.isPresent() && lvl <= 3; lvl++) {
            err("parent[" + lvl + "]      : " + p.get().info().command().orElse("<hidden>"));
            p = p.get().parent();
        }
        err("detected shell : " + shell());
    }

    static String shell() {
        var p = ProcessHandle.current().parent();
        for (var lvl = 0; p.isPresent() && lvl < 3; lvl++) {
            var exe = p.get().info().command().orElse("").toLowerCase();
            var name = exe.substring(exe.lastIndexOf('\\') + 1);
            if (name.startsWith("cmd.")) return "cmd";
            if (name.startsWith("powershell.") || name.startsWith("pwsh.")) return "powershell";
            if (name.startsWith("bash.") || name.startsWith("sh.") || name.startsWith("zsh.")) return "bash";
            p = p.get().parent();
        }
        return "unknown";
    }

    static String cdFor(String shell, String dir) {
        return switch (shell) {
            case "powershell" -> "Set-Location -LiteralPath '" + dir.replace("'", "''") + "'";
            case "bash"       -> "cd \"" + dir + "\"";
            default           -> "cd /d \"" + dir + "\"";
        };
    }

    static String fileType(int std) throws Throwable {
        var h = (MemorySegment) GetStdHandle.invokeExact(std);
        return switch ((int) GetFileType.invokeExact(h)) {
            case 1 -> "DISK (redirected to file)"; case 2 -> "CHAR (console)";
            case 3 -> "PIPE (piped/redirected)";   default -> "UNKNOWN";
        };
    }

    static String consoleMode(int std) throws Throwable {
        try (var a = Arena.ofConfined()) {
            var h = (MemorySegment) GetStdHandle.invokeExact(std);
            var m = a.allocate(JAVA_INT);
            return (int) GetConsoleMode.invokeExact(h, m) != 0 ? "0x" + Integer.toHexString(m.get(JAVA_INT, 0)) : "n/a";
        }
    }

    static int err(MemorySegment cap) { return (int) LAST_ERR.get(cap, 0L); }
    static void err(String s) { System.err.println(s); }
    static void out(String s) { System.out.println(s); }
    static String opt(List<String> a, String k, String d) { var i = a.indexOf(k); return i >= 0 && i + 1 < a.size() ? a.get(i + 1) : d; }
}
