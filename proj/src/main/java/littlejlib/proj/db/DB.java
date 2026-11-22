package littlejlib.proj.db;

import com.arcadedb.ContextConfiguration;
import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.Database;
import com.arcadedb.engine.ComponentFile;
import com.arcadedb.server.ArcadeDBServer;
import littlejlib.proj.model.CentralArtifact;
import littlejlib.proj.model.ProjectDependency;
import littlejlib.proj.model.ProjectRecord;
import datapotter.arcadedbhelper.InitDoc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Singleton database service for ArcadeDB lifecycle management.
 * Manages server initialization, schema setup, and database access.
 *
 * NOTE: This uses its own database separate from maven_project_handler to avoid locking issues.
 */
public class DB {

    private static final Logger log = Logger.getLogger(DB.class.getName());
    private static final String DB_NAME = "proj_cache";
    private static final String DEFAULT_ROOT_PASSWORD = "admin12345";

    private static DB instance;
    private ArcadeDBServer server;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private DB() {
        init();
        // Register shutdown hook to ensure clean shutdown before LogManager cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }, "ArcadeDB-Shutdown-Hook"));
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
        return instance != null && instance.isInitialized.get()
                && instance.server != null && instance.server.isStarted();
    }

    private void init() {
        if (isInitialized.get()) {
            log.warning("Database already initialized");
            return;
        }

        if (littlejlib.proj.Main.isVerbose()) {
            log.info("Initializing ArcadeDB database...");
        }

        try {
            // Create database directory in user home
            Path dbDir = getDbPath();
            Files.createDirectories(dbDir);

            // Configure global settings
            GlobalConfiguration.SERVER_ROOT_PATH.setValue(dbDir.toString());
            GlobalConfiguration.PROFILE.setValue("low-ram");
            GlobalConfiguration.SERVER_ROOT_PASSWORD.setValue(DEFAULT_ROOT_PASSWORD);
            GlobalConfiguration.SERVER_METRICS.setValue(false);

            QuietShutdownLogger.install();
            server = new ArcadeDBServer(new ContextConfiguration());
            server.start();
            if (littlejlib.proj.Main.isVerbose()) {
                log.info("ArcadeDB server started");
            }

            // Create database if it doesn't exist
            if (!server.existsDatabase(DB_NAME)) {
                if (littlejlib.proj.Main.isVerbose()) {
                    log.info("Creating database: " + DB_NAME);
                }
                server.createDatabase(DB_NAME, ComponentFile.MODE.READ_WRITE);
            }

            // Initialize schema - DO NOT CLOSE the database instance
            Database schemaDbInstance = server.getDatabase(DB_NAME);
            if (schemaDbInstance == null || !schemaDbInstance.isOpen()) {
                throw new IllegalStateException("Failed to get valid database instance");
            }

            initSchema(schemaDbInstance);
            // DO NOT CLOSE schemaDbInstance - server manages lifecycle

            isInitialized.set(true);
            if (littlejlib.proj.Main.isVerbose()) {
                log.info("Database initialization complete");
            }

        } catch (Exception e) {
            log.severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void initSchema(Database db) {
        try {
            InitDoc.initDocTypes(db, ProjectRecord.TYPEDEF, CentralArtifact.TYPEDEF, ProjectDependency.TYPEDEF,
                    littlejlib.proj.model.FileRecord.TYPEDEF,
                    littlejlib.proj.workspace.WorkspaceRecord.TYPEDEF,
                    littlejlib.proj.prp.PrpSession.TYPEDEF,
                    littlejlib.proj.prp.ProjectKey.TYPEDEF);
            if (littlejlib.proj.Main.isVerbose()) {
                log.info("Database schema initialized successfully");
            }
        } catch (Exception e) {
            log.severe("Database schema initialization failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Another database in the same server, created on first ask. The transcript index uses this:
     * one server, one lock, one daemon that already knows how to lend its answers to a CLI.
     * DO NOT CLOSE the returned instance - the server owns it.
     */
    public synchronized Database getSecondary(String name) {
        if (!isInitialized.get() || server == null || !server.isStarted()) {
            throw new IllegalStateException("Database service not initialized");
        }
        if (!server.existsDatabase(name)) server.createDatabase(name, ComponentFile.MODE.READ_WRITE);
        var db = server.getDatabase(name);
        if (db == null || !db.isOpen()) throw new IllegalStateException("cannot open database " + name);
        return db;
    }

    /**
     * Gets database instance - DO NOT CLOSE returned instance
     */
    public Database getDatabase() {
        if (!isInitialized.get() || server == null || !server.isStarted()) {
            throw new IllegalStateException("Database service not initialized");
        }
        return server.getDatabase(DB_NAME);
    }

    public synchronized void shutdown() {
        if (!isInitialized.getAndSet(false)) {
            return;
        }

        if (littlejlib.proj.Main.isVerbose()) {
            log.info("Shutting down database...");
        }

        if (server != null && server.isStarted()) {
            try {
                // Close all databases first
                try {
                    Database db = server.getDatabase(DB_NAME);
                    if (db != null && db.isOpen()) {
                        db.close();
                    }
                } catch (Exception e) {
                    // Database might already be closed, ignore
                }

                // Now stop the server - this is a BLOCKING call
                server.stop();

                if (littlejlib.proj.Main.isVerbose()) {
                    log.info("Database shutdown complete");
                }
            } catch (Exception e) {
                if (littlejlib.proj.Main.isVerbose()) {
                    log.severe("Error during shutdown: " + e.getMessage());
                }
            } finally {
                server = null;
            }
        }
    }

    private Path getDbPath() {
        return littlejlib.proj.util.AppPaths.arcadeDb();
    }
}
