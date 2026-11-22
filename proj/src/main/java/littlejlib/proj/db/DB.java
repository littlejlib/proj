package littlejlib.proj.db;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.engine.ComponentFile;
import datapotter.arcadedbhelper.InitDoc;
import littlejlib.proj.model.CentralArtifact;
import littlejlib.proj.model.ProjectDependency;
import littlejlib.proj.model.ProjectRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Singleton database service: opens the embedded ArcadeDB engine directly, one DatabaseFactory per database, under
 * {@code ~/littlejlib/proj/arcadedb/databases/<name>}. The engine's exclusive directory lock is what keeps a second
 * process out. It used to run ArcadeDB's whole server (HTTP, security, metrics) just to host these databases; that
 * is what made the exe large, and the on-disk layout is the same, so existing indexes open unchanged.
 *
 * NOTE: This uses its own database separate from maven_project_handler to avoid locking issues.
 */
public class DB {

    private static final Logger log = Logger.getLogger(DB.class.getName());
    private static final String DB_NAME = "proj_cache";

    private static DB instance;
    private final Map<String, DatabaseFactory> factories = new LinkedHashMap<>();
    private final Map<String, Database> databases = new LinkedHashMap<>();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private DB() {
        init();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "ArcadeDB-Shutdown-Hook"));
    }

    public static synchronized DB get() {
        if (instance == null) {
            instance = new DB();
        }
        return instance;
    }

    /**
     * Whether THIS process already holds the database open. The lock is exclusive, so a caller that
     * wants data has to know whether reading it here is free or impossible - see
     * {@code prp/PrpSessions}.
     */
    public static synchronized boolean isOpen() {
        return instance != null && instance.isInitialized.get() && !instance.databases.isEmpty();
    }

    private void init() {
        if (isInitialized.get()) {
            log.warning("Database already initialized");
            return;
        }
        try {
            Files.createDirectories(getDbPath());
            GlobalConfiguration.PROFILE.setValue("low-ram");
            QuietShutdownLogger.install();
            var main = open(DB_NAME);
            initSchema(main);
            isInitialized.set(true);
            if (littlejlib.proj.Main.isVerbose()) log.info("Database initialization complete");
        } catch (Exception e) {
            log.severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private synchronized Database open(String name) {
        var existing = databases.get(name);
        if (existing != null && existing.isOpen()) return existing;
        var factory = new DatabaseFactory(getDbPath().resolve("databases").resolve(name).toString());
        var db = factory.exists() ? factory.open(ComponentFile.MODE.READ_WRITE) : factory.create();
        if (db == null || !db.isOpen()) throw new IllegalStateException("cannot open database " + name);
        factories.put(name, factory);
        databases.put(name, db);
        return db;
    }

    private void initSchema(Database db) {
        InitDoc.initDocTypes(db, ProjectRecord.TYPEDEF, CentralArtifact.TYPEDEF, ProjectDependency.TYPEDEF,
                littlejlib.proj.model.FileRecord.TYPEDEF,
                littlejlib.proj.workspace.WorkspaceRecord.TYPEDEF,
                littlejlib.proj.prp.PrpSession.TYPEDEF,
                littlejlib.proj.prp.ProjectKey.TYPEDEF);
    }

    /**
     * Another database next to the main one, created on first ask. The transcript index uses this:
     * one lock, one daemon that already knows how to lend its answers to a CLI.
     * DO NOT CLOSE the returned instance - this class owns it.
     */
    public synchronized Database getSecondary(String name) {
        if (!isInitialized.get()) throw new IllegalStateException("Database service not initialized");
        return open(name);
    }

    /**
     * Gets database instance - DO NOT CLOSE returned instance
     */
    public Database getDatabase() {
        if (!isInitialized.get()) throw new IllegalStateException("Database service not initialized");
        return open(DB_NAME);
    }

    public synchronized void shutdown() {
        if (!isInitialized.getAndSet(false)) return;
        for (var db : databases.values()) {
            try {
                if (db.isOpen()) db.close();
            } catch (Exception e) {
                // already closed
            }
        }
        for (var factory : factories.values()) {
            try {
                factory.close();
            } catch (Exception e) {
                if (littlejlib.proj.Main.isVerbose()) log.severe("Error during shutdown: " + e.getMessage());
            }
        }
        databases.clear();
        factories.clear();
    }

    private Path getDbPath() {
        return littlejlib.proj.util.AppPaths.arcadeDb();
    }
}
