package littlejlib.proj.precompact;

import module java.base;

import com.google.gson.JsonParser;
import datapotter.datahelper.Data;

/** The JSON claude-code sends a PreCompact hook on stdin. */
@Data
public final class PreCompactInput extends PreCompactInput_A {

    String sessionId;
    String transcriptPath;
    String cwd;
    String trigger;

    public boolean auto() {
        return "auto".equals(trigger) && sessionId != null && !sessionId.isBlank();
    }

    /** @return null when stdin is not the JSON of a hook, which must never block anything */
    public static PreCompactInput read(InputStream in) {
        try {
            var text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var o = JsonParser.parseString(text).getAsJsonObject();
            return new PreCompactInput().sessionId(str(o, "session_id")).transcriptPath(str(o, "transcript_path"))
                    .cwd(str(o, "cwd")).trigger(str(o, "trigger"));
        } catch (IOException | RuntimeException e) {
            PreCompactState.log("hook: parse failed: " + e);
            return null;
        }
    }

    static String str(com.google.gson.JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
