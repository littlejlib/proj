package littlejlib.proj.track;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A previous session within a token budget, for the next session to read instead of a log's tail.
 * Kept first: every human turn and Claude's last reply to it (newest turns win when even that does
 * not fit). Then Claude's other replies, newest first, while the budget lasts. Printed in order.
 */
public final class Recall {

    private Recall() { }

    public static final long DEFAULT_BUDGET = 40_000;

    static final int MAX_BLOCK = 6000, MAX_SUMMARY_SHARE = 4;

    public static List<String> render(Conversation c, long budgetTokens) {
        var maxChars = budgetTokens * 4;
        var out = new ArrayList<String>();
        var used = 0L;
        if (c.summary() != null) {
            var s = cap(c.summary(), (int) Math.min(Integer.MAX_VALUE, maxChars / MAX_SUMMARY_SHARE));
            out.add("## compaction summary of the session");
            out.add(s);
            used += s.length();
        }
        var turns = c.turns();
        var keepHuman = new HashSet<Integer>();
        var keepReply = new HashSet<String>();
        for (var i = turns.size() - 1; i >= 0; i--) {
            var t = turns.get(i);
            var cost = len(t.human()) + (t.replies().isEmpty() ? 0 : len(last(t)));
            if (used + cost > maxChars) break;
            used += cost;
            keepHuman.add(i);
            if (!t.replies().isEmpty()) keepReply.add(key(i, t.replies().size() - 1));
        }
        for (var i = turns.size() - 1; i >= 0; i--) {
            if (!keepHuman.contains(i)) continue;
            var replies = turns.get(i).replies();
            for (var j = replies.size() - 2; j >= 0; j--) {
                var cost = len(replies.get(j));
                if (used + cost > maxChars) continue;
                used += cost;
                keepReply.add(key(i, j));
            }
        }
        print(out, turns, keepHuman, keepReply);
        return out;
    }

    static void print(List<String> out, List<Turn> turns, Set<Integer> keepHuman, Set<String> keepReply) {
        var droppedTurns = turns.size() - keepHuman.size();
        if (droppedTurns > 0) out.add("(" + droppedTurns + " earlier turns left out by the budget)");
        for (var i = 0; i < turns.size(); i++) {
            if (!keepHuman.contains(i)) continue;
            var t = turns.get(i);
            if (t.human() != null) {
                out.add("## user  " + Now.shown(t.at()));
                out.add(cap(t.human(), MAX_BLOCK));
            }
            var left = 0;
            var first = true;
            for (var j = 0; j < t.replies().size(); j++) {
                if (!keepReply.contains(key(i, j))) {
                    left++;
                    continue;
                }
                if (first) out.add("## claude");
                first = false;
                out.add(cap(t.replies().get(j), MAX_BLOCK));
            }
            if (left > 0) out.add("(" + left + " earlier replies to this turn left out)");
        }
    }

    static String last(Turn t) {
        return t.replies().get(t.replies().size() - 1);
    }

    static String key(int turn, int reply) {
        return turn + ":" + reply;
    }

    static long len(String s) {
        return s == null ? 0 : Math.min(s.length(), MAX_BLOCK) + 1;
    }

    static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + " [... cut, " + (s.length() - max) + " more characters]";
    }
}
