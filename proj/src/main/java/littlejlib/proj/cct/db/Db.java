package littlejlib.proj.cct.db;

import module java.base;

import com.arcadedb.database.Database;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.StorePaths;
import xyz.jphil.arcadedb.datahelper.InitDoc;

/**
 * The transcript index, hosted as a second database inside the ArcadeDB server proj already runs.
 * <p>
 * It used to be its own {@code DatabaseFactory} under {@code ~/xyz-jphil/ccsearchconv}, which meant
 * one exclusive directory lock per process and therefore a standalone CLI that got locked out
 * whenever the window was open. Sharing proj's server removes the whole problem: the daemon owns
 * one server, a query routes to the daemon when there is one, and a query with no daemon starts a
 * server of its own - the same arrangement the project index has always had.
 */
public final class Db implements Closeable {

    public static final String NAME = "cctranscripts";

    private static Db instance;
    private Database database;

    private Db() { open(); }

    public static synchronized Db get() {
        if (instance == null) instance = new Db();
        return instance;
    }

    private void open() {
        Quiet.suppress();
        StorePaths.ensureAppHome();
        try {
            database = littlejlib.proj.db.DB.get().getSecondary(NAME);
            InitDoc.initDocTypes(database,
                ProjectDoc.TYPEDEF, SessionDoc.TYPEDEF, TurnDoc.TYPEDEF, HistoryDoc.TYPEDEF);
        } catch (Exception e) {
            throw new IllegalStateException(diagnose(e), e);
        }
    }

    private static String diagnose(Exception e) {
        var msg = String.valueOf(e.getMessage());
        if (msg.contains("locked") || msg.contains("in use") || e instanceof OverlappingFileLockException)
            return "the index is held by another proj instance; quit it from the system tray and retry";
        return "cannot open the transcript index: " + msg;
    }

    public Database db() {
        if (database == null) throw new IllegalStateException("index not open");
        return database;
    }

    /**
     * Deliberately does NOT close the database. The server owns it, proj's own index lives in the
     * same server, and closing it here would take the picker's project list down with it.
     */
    @Override
    public synchronized void close() {
        database = null;
        instance = null;
    }

    public void shutdown() { close(); }
}
