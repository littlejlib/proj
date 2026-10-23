package littlejlib.proj.hib;

import module java.base;

import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpStatus;

/**
 * The PRP a conversation is working on, from every PRP its transcript refers to that exists on disk
 * now - so a PRP created halfway through counts. Each mention scores by who made it (see
 * {@link PrpRefs}) and is weighted up the later it comes; the PRP first typed by the human and one
 * whose number is in the conversation's name get a bonus. The nearest tier wins when it holds a real
 * share of the top score: the own {@code prp} folder, then a subfolder, then another project.
 */
public final class SessionPrp {

    private SessionPrp() { }

    /** @param sure false when the evidence is thin or a close runner-up exists: the caller must ask, not guess */
    public record Pick(PrpEntry entry, String why, List<String> table, boolean sure) {
        public Pick(PrpEntry entry, String why, List<String> table) { this(entry, why, table, true); }
    }

    static final Pattern INDEX_IN_NAME = Pattern.compile("-(\\d{2})(?:-|$)");
    static final double FIRST_TYPED = 15, NAMED = 15, SHARE = 0.3, CLOSED = 0.5, MIN_SURE = 10, MARGIN = 0.8;
    static final String[] TIERS = {"own prp folder", "a subfolder", "another project"};

    public static PrpEntry of(LiveSession s, Path transcript) {
        var p = pick(s, transcript);
        return p == null ? null : p.entry();
    }

    public static Pick pick(LiveSession s, Path transcript) {
        if (s.cwd() == null || transcript == null) return null;
        var cwd = Path.of(s.cwd()).toAbsolutePath().normalize();
        var own = cwd.resolve("prp");
        var refs = PrpRefs.scan(transcript, cwd);
        var dirs = new LinkedHashMap<Path, List<PrpEntry>>();
        dirs.put(own, list(own));
        refs.stream().map(PrpRefs.Ref::dir).filter(Objects::nonNull).distinct().forEach(d -> dirs.computeIfAbsent(d, SessionPrp::list));

        // Identity, not equals: PrpEntry equality is the number alone, so every project's PRP 01 would be one key.
        var score = new IdentityHashMap<PrpEntry, Double>();
        var mentions = new IdentityHashMap<PrpEntry, Integer>();
        PrpEntry firstTyped = null;
        for (var r : refs) {
            var e = r.dir() != null ? byIndex(dirs.get(r.dir()), r.index()) : byStem(dirs, r.stem());
            if (e == null) continue;
            score.merge(e, r.weight() * (1 + r.position()), Double::sum);
            mentions.merge(e, 1, Integer::sum);
            if (firstTyped == null && r.typed()) firstTyped = e;
        }
        if (firstTyped != null) score.merge(firstTyped, FIRST_TYPED, Double::sum);
        var nameIndex = nameIndex(s);
        score.keySet().stream().filter(e -> e.getIndex().equals(nameIndex)).toList().forEach(e -> score.merge(e, NAMED, Double::sum));
        // A closed PRP is usually the previous cycle; the open one is the work in hand.
        score.keySet().stream().filter(e -> e.getStatus() == PrpStatus.CLOSED).toList().forEach(e -> score.put(e, score.get(e) * CLOSED));
        if (score.isEmpty()) {
            var named = byIndex(dirs.get(own), nameIndex);
            return named == null ? null : new Pick(named, "number in the conversation's name", List.of());
        }
        var best = best(score, cwd);
        var table = score.entrySet().stream().sorted(Map.Entry.<PrpEntry, Double>comparingByValue().reversed()).limit(8)
                .map(e -> String.format("%7.1f  %-15s %3d mentions  %s", e.getValue(), TIERS[tier(e.getKey(), cwd)],
                        mentions.get(e.getKey()), e.getKey().getMainFile())).toList();
        var top = score.get(best);
        var runnerUp = score.entrySet().stream().filter(e -> e.getKey() != best).mapToDouble(Map.Entry::getValue).max().orElse(0);
        var sure = top >= MIN_SURE && runnerUp < MARGIN * top;
        return new Pick(best, TIERS[tier(best, cwd)] + ", " + mentions.get(best) + " mentions, score "
                + Math.round(top) + " of " + score.size() + " PRPs referred to" + (sure ? "" : " - UNCERTAIN"), table, sure);
    }

    /**
     * The nearest tier wins, but only with a real share of the attention: a PRP in the own folder that
     * is mentioned once must not beat the one in another project the conversation is actually about.
     */
    static PrpEntry best(Map<PrpEntry, Double> score, Path cwd) {
        var top = Collections.max(score.values());
        for (var t = 0; t <= 2; t++) {
            var tier = t;
            var hit = score.entrySet().stream().filter(e -> tier(e.getKey(), cwd) == tier)
                    .max(Map.Entry.comparingByValue()).filter(e -> e.getValue() >= SHARE * top);
            if (hit.isPresent()) return hit.get().getKey();
        }
        return score.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }

    /** {@code --prp}: a PRP file, or a number in the conversation's own prp folder. */
    public static PrpEntry given(String wanted, Path cwd) {
        if (wanted.strip().matches("\\d{1,2}"))
            return byIndex(list(cwd.resolve("prp")), String.format("%02d", Integer.parseInt(wanted.strip())));
        var file = cwd.resolve(wanted.strip()).toAbsolutePath().normalize();
        return list(file.getParent()).stream()
                .filter(e -> e.getMainFile().toAbsolutePath().normalize().equals(file)).findFirst().orElse(null);
    }

    static int tier(PrpEntry e, Path cwd) {
        var dir = e.getMainFile().toAbsolutePath().normalize().getParent();
        if (dir.equals(cwd.resolve("prp"))) return 0;
        return dir.startsWith(cwd) ? 1 : 2;
    }

    /** A bare name: the conversation's own folder first, then any folder the transcript named. */
    static PrpEntry byStem(Map<Path, List<PrpEntry>> dirs, String stem) {
        for (var list : dirs.values())
            for (var e : list) if (PrpRefs.names(stem, e.getMainFile())) return e;
        return null;
    }

    static String nameIndex(LiveSession s) {
        if (s.name() == null) return null;
        var m = INDEX_IN_NAME.matcher(s.name());
        return m.find() ? m.group(1) : null;
    }

    static PrpEntry byIndex(List<PrpEntry> list, String index) {
        if (list == null || index == null) return null;
        return list.stream().filter(p -> index.equals(p.getIndex())).findFirst().orElse(null);
    }

    static List<PrpEntry> list(Path prpDir) {
        if (prpDir == null || !Files.isDirectory(prpDir)) return List.of();
        try {
            return PrpCreator.list(prpDir);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }
}
