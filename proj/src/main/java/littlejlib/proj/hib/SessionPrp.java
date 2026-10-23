package littlejlib.proj.hib;

import module java.base;

import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpInstructions;

/**
 * The PRP a conversation is working on: the one its first prompt named, else the {@code -NN-} in
 * its name ({@code proj-41-hibernate}), looked up in {@code <cwd>/prp}. Null means it is not on a PRP.
 */
public final class SessionPrp {

    private SessionPrp() { }

    static final Pattern INDEX_IN_NAME = Pattern.compile("-(\\d{2})(?:-|$)");

    public static PrpEntry of(LiveSession s, Path transcript) {
        if (s.cwd() == null) return null;
        var prpDir = PrpCreator.prpDirOf(Path.of(s.cwd()));
        if (!Files.isDirectory(prpDir)) return null;
        List<PrpEntry> all;
        try {
            all = PrpCreator.list(prpDir);
        } catch (IOException e) {
            return null;
        }
        var index = transcript == null ? null : opened(transcript);
        if (index == null && s.name() != null) {
            var m = INDEX_IN_NAME.matcher(s.name());
            if (m.find()) index = m.group(1);
        }
        if (index == null) return null;
        var wanted = index;
        return all.stream().filter(p -> wanted.equals(p.getIndex())).findFirst().orElse(null);
    }

    static String opened(Path transcript) {
        try {
            return PrpInstructions.openedOn(transcript);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
