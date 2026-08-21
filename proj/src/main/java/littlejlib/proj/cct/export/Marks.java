package littlejlib.proj.cct.export;

/**
 * The characters claude-code's own {@code /export} uses, reused here on purpose.
 * <p>
 * Verified against the real exports in this repo's prp folder rather than assumed: a user turn opens
 * with U+276F and an assistant turn with U+25CF, both at the start of a line, and a tool result is
 * introduced by U+23BF. Using the same characters means one grep works on both our exports and any
 * `/export` output the user already has, and it means these files LOOK like the logs they are used
 * to reading.
 * <p>
 * The reason they are worth more here than there: in a markdown heading they make the speaker
 * greppable in a way a word never is. {@code ^## ❯} finds every human turn and nothing else -
 * not a `##` that happens to appear inside a transcript, not the word "user" in prose.
 */
public final class Marks {

    private Marks() {}

    public static final String
        USER = "❯",
        AGENT = "●",
        RESULT = "⎿",
        THINKING = "✻",
        ELIDED = "…";
}
