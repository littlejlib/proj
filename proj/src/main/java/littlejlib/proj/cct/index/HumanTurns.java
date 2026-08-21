package littlejlib.proj.cct.index;

import module java.base;

import littlejlib.proj.cct.model.TurnDoc;
import littlejlib.proj.cct.store.Layer;

import static littlejlib.proj.cct.Num.*;

/**
 * ONE typed prompt is written to the transcript several times, and only the last one is whole.
 * <p>
 * Measured on this machine, indexing 854 sessions. A single message appears as three
 * {@code "type":"user"} records with {@code promptSource:"typed"} and content that grows each time -
 * 1171, then 1288, then 1586 bytes. Indexed naively the viewer shows the same paragraph two or three
 * times, each copy a prefix of the next, and every match count for the session is inflated.
 * <p>
 * THE OBVIOUS RULE - group by parentUuid and take the last, as {@code ai-title} needs - is not
 * enough, and the measurements say why. Two shapes defeat it:
 * <ul>
 *   <li>the copies are NOT adjacent: seq 1350 and seq 1355 share a parentUuid with five records in
 *       between, so anything holding back only the previous turn has already emitted the first;</li>
 *   <li>the parentUuid CHANGES between copies, and even cycles back - one prompt was re-emitted four
 *       times across three different parents - while others carry no parentUuid at all.</li>
 * </ul>
 * So the rule is on the TEXT: within one session, a human turn whose text is a prefix of a later
 * human turn's text is a draft of it and is dropped. Prefix, not similarity - the shorter one's
 * content is wholly contained in the longer by definition, so nothing is lost but the knowledge that
 * a half-typed version existed. The raw jsonl still has that if it is ever wanted.
 * <p>
 * Human turns are buffered for the whole file to make this possible, which costs nothing: a session
 * of 1042 records held 24 of them.
 */
public final class HumanTurns {

    private final Consumer<TurnDoc> other;
    private final List<TurnDoc> fresh = new ArrayList<>();
    private final List<TurnDoc> stored;

    /**
     * @param stored human turns already in the index from an earlier pass over the same file, so a
     *               prompt re-emitted across a tail boundary is still resolved. Empty on a rebuild.
     */
    public HumanTurns(List<TurnDoc> stored, Consumer<TurnDoc> other) {
        this.stored = stored == null ? List.of() : stored;
        this.other = other;
    }

    public void accept(TurnDoc t) {
        if (Layer.ofDepth(n(t.layer())) == Layer.HUMAN) fresh.add(t);
        else other.accept(t);
    }

    /** The turns to keep, in seq order, and the keys of stored turns that a fresh one supersedes. */
    public Resolved resolve() {
        var all = new ArrayList<TurnDoc>(stored.size() + fresh.size());
        all.addAll(stored);
        all.addAll(fresh);
        all.sort(Comparator.comparingInt(t -> n(t.seq())));

        var survivors = new ArrayList<TurnDoc>(all.size());
        for (var i = 0; i < all.size(); i++) if (!supersededByLater(all, i)) survivors.add(all.get(i));

        var keys = new HashSet<String>();
        for (var t : survivors) keys.add(t.turnKey());
        var drop = new ArrayList<String>();
        for (var t : stored) if (!keys.contains(t.turnKey())) drop.add(t.turnKey());
        var keep = new ArrayList<TurnDoc>();
        for (var t : fresh) if (keys.contains(t.turnKey())) keep.add(t);
        return new Resolved(keep, drop, survivors.size());
    }

    static boolean supersededByLater(List<TurnDoc> all, int at) {
        var mine = flat(all.get(at).text());
        if (mine.isEmpty()) return false;
        for (var j = at + 1; j < all.size(); j++) {
            var later = flat(all.get(j).text());
            if (later.length() >= mine.length() && later.startsWith(mine)) return true;
        }
        return false;
    }

    static String flat(String s) { return s == null ? "" : s.strip(); }

    public record Resolved(List<TurnDoc> keep, List<String> dropKeys, int total) { }
}
