package littlejlib.proj.hib;

import module java.base;

/**
 * Ends a conversation and closes its tab: claude's descendants (MCP servers and the like, which
 * would otherwise be orphaned holding RAM), then claude, then the tab's shell. When the shell is not
 * under a known terminal it is left alone, so nothing else is closed by accident.
 * <p>
 * Safe to call from inside the conversation ({@code !proj hib}): this process and its ancestors are
 * spared until the end, and claude dies before the {@code !} command returns, so no model turn starts
 * on its output.
 */
public final class Closer {

    private Closer() { }

    public static boolean end(LiveSession s) {
        var claude = ProcessHandle.of(s.pid()).orElse(null);
        if (claude == null) return true;
        var spared = new HashSet<Long>();
        for (var p = Optional.of(ProcessHandle.current()); p.isPresent(); p = p.get().parent()) spared.add(p.get().pid());
        claude.descendants().filter(d -> !spared.contains(d.pid())).forEach(ProcessHandle::destroyForcibly);
        claude.destroyForcibly();
        try {
            claude.onExit().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
        if (s.shellPid() != null) ProcessHandle.of(s.shellPid()).ifPresent(ProcessHandle::destroyForcibly);
        return true;
    }
}
