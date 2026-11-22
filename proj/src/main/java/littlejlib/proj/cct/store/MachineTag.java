package littlejlib.proj.cct.store;

import module java.base;

public final class MachineTag {

    private MachineTag() {}

    private static final List<String> OPENERS = List.of(
        "<task-notification", "<system-reminder", "<local-command-stdout",
        "<local-command-stderr", "<local-command-caveat", "<bash-stdout",
        "<bash-stderr", "<persisted-output", "<tool-use-error", "<function_results",
        "<command-name", "<command-message", "<command-args");

    public static boolean isMachine(String content) {
        if (content == null) return true;
        var s = content.stripLeading();
        if (s.isEmpty()) return true;
        if (s.charAt(0) != '<') return false;
        for (var o : OPENERS) if (s.regionMatches(true, 0, o, 0, o.length())) return true;
        return false;
    }

    public static boolean isHuman(String content) { return !isMachine(content); }
}
