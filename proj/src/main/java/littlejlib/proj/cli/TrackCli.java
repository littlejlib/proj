package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.prp.PrpUuid;
import littlejlib.proj.prp.Base34;

import java.io.IOException;
import java.nio.file.Path;

/** What the timeline, meta and work-plan commands share: one way to fail, one way to name a PRP. */
final class TrackCli {

    private TrackCli() { }

    interface Body {
        void run() throws IOException;
    }

    /** Bad input exits 2 with its message, a file problem exits 3. */
    static void run(Body body) {
        try {
            body.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            PrpCommands.ERR.println(e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            PrpCommands.ERR.println("could not read or write: " + e.getMessage());
            System.exit(3);
        }
    }

    static String uuid(PrpEntry e) {
        var u = PrpUuid.read(e.getMainFile());
        if (u == null) throw new IllegalStateException("could not give " + e.getMainFile().getFileName() + " a uuid");
        return u;
    }

    /** A PRP of this folder by number, or any PRP by its whole uuid; the answer is a uuid. */
    static String target(Path prpDir, String typed) {
        var n = Base34.normalize(typed);
        if (Base34.valid(n)) return n;
        return uuid(PrpCommands.resolve(prpDir, typed));
    }
}
