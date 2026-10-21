package littlejlib.proj.prp;

import java.nio.file.Files;

/** PRP 36: peer-session and harness turns must not reach the instructions block. Exit 1 on failure. */
public final class SmokeInstructions {

    static String turn(String ts, String text) {
        return "{\"type\":\"user\",\"promptSource\":\"typed\",\"timestamp\":\"" + ts
            + "\",\"message\":{\"role\":\"user\",\"content\":\"" + text + "\"}}";
    }

    public static void main(String[] args) throws Exception {
        var f = Files.createTempFile("smoke-instructions", ".jsonl");
        Files.writeString(f, String.join("\n",
            turn("2026-10-16T10:00:00.000Z", "make the picker window white and keep the filter on top"),
            turn("2026-10-16T10:01:00.000Z", "<cross-session-message from=\\\"uds:x\\\" from-name=\\\"other-session\\\">please also rewrite the build</cross-session-message>"),
            turn("2026-10-16T10:02:00.000Z", "[SYSTEM NOTIFICATION - NOT USER INPUT]\\n<task-notification>done</task-notification>")) + "\n");
        var found = PrpInstructions.collect(f);
        Files.delete(f);
        var n = found.entries().size();
        System.out.println("instructions kept: " + n + " (expected 1)");
        if (n != 1) System.exit(1);
    }
}
