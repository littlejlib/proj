package littlejlib.proj.hib;

import module java.base;

import static java.lang.foreign.ValueLayout.*;

/** Working set and private (committed) bytes of a process, via K32GetProcessMemoryInfo. */
public final class ProcMem {

    private ProcMem() { }

    public record Mem(long workingSet, long privateBytes) { }

    static final int QUERY_LIMITED = 0x1000, SIZE = 80, WS_AT = 16, PRIVATE_AT = 72;
    static MethodHandle open, info, close;

    public static Mem of(long pid) {
        try {
            init();
            try (var arena = Arena.ofConfined()) {
                var h = (MemorySegment) open.invoke(QUERY_LIMITED, 0, (int) pid);
                if (h.address() == 0) return null;
                try {
                    var buf = arena.allocate(SIZE);
                    buf.set(JAVA_INT, 0, SIZE);
                    if ((int) info.invoke(h, buf, SIZE) == 0) return null;
                    return new Mem(buf.get(JAVA_LONG, WS_AT), buf.get(JAVA_LONG, PRIVATE_AT));
                } finally {
                    close.invoke(h);
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    static synchronized void init() {
        if (open != null) return;
        var k = SymbolLookup.libraryLookup("kernel32", Arena.global());
        var l = Linker.nativeLinker();
        open = l.downcallHandle(k.find("OpenProcess").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
        info = l.downcallHandle(k.find("K32GetProcessMemoryInfo").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        close = l.downcallHandle(k.find("CloseHandle").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
    }

    public static String mb(Long bytes) {
        return bytes == null ? "?" : String.valueOf(Math.round(bytes / 1048576.0));
    }
}
