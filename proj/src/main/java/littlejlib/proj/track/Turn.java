package littlejlib.proj.track;

import java.util.ArrayList;
import java.util.List;

/** One human turn and Claude's text replies to it, in order. {@code human} is null for replies before any turn. */
public final class Turn {

    final String at;
    String human;
    final List<String> replies = new ArrayList<>();

    Turn(String at, String human) {
        this.at = at;
        this.human = human;
    }

    public String at() {
        return at;
    }

    public String human() {
        return human;
    }

    public List<String> replies() {
        return replies;
    }
}
