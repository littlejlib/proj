package littlejlib.proj.track;

import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpStatus;
import littlejlib.proj.prp.PrpUuid;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** The PRPs of one prp folder by uuid, read without writing: for showing a uuid as a number and for checks. */
public final class PrpRefs {

    final Map<String, PrpEntry> byUuid = new HashMap<>();

    public static PrpRefs of(Path prpDir) throws IOException {
        var refs = new PrpRefs();
        for (var e : PrpCreator.list(prpDir)) {
            var u = PrpUuid.read(e.getMainFile());
            if (u != null) refs.byUuid.put(u, e);
        }
        return refs;
    }

    public PrpEntry get(String uuid) {
        return uuid == null ? null : byUuid.get(uuid);
    }

    /** The PRP number for a uuid of this folder, else the uuid's first symbols, else "-". */
    public String label(String uuid) {
        var e = get(uuid);
        return e != null ? e.getIndex() : uuid == null ? "-" : uuid.substring(0, Math.min(6, uuid.length())) + "..";
    }

    public boolean closed(String uuid) {
        var e = get(uuid);
        return e != null && e.getStatus() == PrpStatus.CLOSED;
    }
}
