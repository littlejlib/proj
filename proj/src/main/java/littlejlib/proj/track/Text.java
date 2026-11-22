package littlejlib.proj.track;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.regex.Pattern;

/** Plain text out of a message's content, and what of a user record is really something a human typed. */
final class Text {

    private Text() { }

    static final Pattern REMINDER = Pattern.compile("(?s)<system-reminder>.*?</system-reminder>");

    static final Pattern MACHINE = Pattern.compile("^\\s*(<command-name>|<command-message>|<local-command-|<task-notification>|"
            + "<bash-|<user-prompt-submit-hook>|Caveat: The messages below|\\[Request interrupted)");

    /**
     * A string, or the text blocks of an array. {@code skipToolResults}: an array carrying a tool result
     * is the tool's answer, not something typed, so it yields nothing.
     */
    static String of(JsonElement content, boolean skipToolResults) {
        if (content == null || content.isJsonNull()) return null;
        if (content.isJsonPrimitive()) return content.getAsString();
        if (!content.isJsonArray()) return null;
        var parts = new ArrayList<String>();
        for (var b : content.getAsJsonArray()) {
            if (b.isJsonPrimitive()) {
                parts.add(b.getAsString());
                continue;
            }
            if (!b.isJsonObject()) continue;
            var type = Conversation.str(b.getAsJsonObject(), "type");
            if (skipToolResults && "tool_result".equals(type)) return null;
            if ("text".equals(type)) parts.add(Conversation.str(b.getAsJsonObject(), "text"));
        }
        return String.join("\n", parts);
    }

    /** The text without system reminders, or null when what is left was written by a machine. */
    static String human(String text) {
        if (text == null) return null;
        var t = REMINDER.matcher(text).replaceAll("").strip();
        return t.isEmpty() || MACHINE.matcher(t).find() ? null : t;
    }
}
