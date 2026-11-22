package littlejlib.proj.ui;

import javafx.application.Platform;

/**
 * Centralized JavaFX platform initialization and dialog management.
 * Ensures the platform initializes exactly once and all dialogs are shown
 * through a consistent, predictable mechanism.
 */
public class JavaFXManager {

    private static volatile JavaFXManager instance;
    private static final Object CLASS_LOCK = new Object();

    private final Object platformLock = new Object();
    private boolean platformInitialized = false;
    private boolean platformStartupRequested = false;

    /**
     * Get the singleton instance
     */
    public static JavaFXManager getInstance() {
        if (instance == null) {
            synchronized (CLASS_LOCK) {
                if (instance == null) {
                    instance = new JavaFXManager();
                }
            }
        }
        return instance;
    }

    /**
     * Ensure JavaFX platform is initialized and ready.
     * Safe to call multiple times - only initializes once.
     */
    public void ensurePlatformInitialized() {
        // Fast path - already initialized
        if (platformInitialized) {
            return;
        }

        synchronized (platformLock) {
            // Double-check in synchronized block
            if (platformInitialized) {
                return;
            }

            // If we're on the FX thread already, platform is ready
            if (Platform.isFxApplicationThread()) {
                platformInitialized = true;
                Platform.setImplicitExit(false); // Disable auto-shutdown when all stages close
                return;
            }

            System.out.println("[JavaFXManager] Initializing JavaFX platform");

            // Try to start the platform
            boolean platformAlreadyRunning = false;
            try {
                if (!platformStartupRequested) {
                    platformStartupRequested = true;
                    Platform.startup(() -> {
                        System.out.println("[JavaFXManager] JavaFX event loop started");
                        // CRITICAL: Disable implicit exit so closing all stages doesn't stop event loop
                        Platform.setImplicitExit(false);
                        synchronized (platformLock) {
                            platformInitialized = true;
                            platformLock.notifyAll();
                        }
                    });
                }
            } catch (IllegalStateException e) {
                // Platform is already running
                platformAlreadyRunning = true;
                System.out.println("[JavaFXManager] Platform already initialized by another component");
            }

            if (platformAlreadyRunning) {
                // Event loop is running but might need time to settle
                System.out.println("[JavaFXManager] Waiting for event loop to settle...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Disable implicit exit in case it wasn't set
                Platform.runLater(() -> Platform.setImplicitExit(false));
                platformInitialized = true;
                System.out.println("[JavaFXManager] Platform ready");
            } else {
                // Wait for our startup callback to fire
                try {
                    long startTime = System.currentTimeMillis();
                    while (!platformInitialized && System.currentTimeMillis() - startTime < 5000) {
                        platformLock.wait(100);
                    }

                    if (!platformInitialized) {
                        System.err.println("[JavaFXManager] ERROR: Platform initialization timeout");
                        platformInitialized = false; // Mark as failed
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                System.out.println("[JavaFXManager] Platform ready");
            }
        }
    }

    /**
     * Show a dialog with blocking semantics.
     * Call from any thread - handles platform initialization and blocking.
     *
     * @param dialogBuilder A lambda that creates and shows the dialog on the FX thread
     * @param timeoutMs Maximum time to wait for dialog to close
     * @return The result from the dialog, or null if timeout/error
     */
    public <T> T showDialog(DialogBuilder<T> dialogBuilder, long timeoutMs) {
        // Ensure platform is ready before attempting to show dialog
        ensurePlatformInitialized();

        if (!platformInitialized) {
            System.err.println("[JavaFXManager] ERROR: Platform failed to initialize");
            return null;
        }

        final Object dialogLock = new Object();
        final Object[] result = new Object[1];

        System.out.println("[JavaFXManager] Scheduling dialog on JavaFX thread");
        Platform.runLater(() -> {
            System.out.println("[JavaFXManager] Creating dialog on JavaFX thread");
            try {
                // Create dialog and set up callback - DialogBuilder must NOT block
                dialogBuilder.build(dialogLock, result);
                System.out.println("[JavaFXManager] Dialog created and shown");
            } catch (Exception e) {
                System.err.println("[JavaFXManager] ERROR: Dialog creation failed: " + e.getMessage());
                e.printStackTrace();
                synchronized (dialogLock) {
                    result[0] = null;
                    dialogLock.notifyAll();
                }
            }
        });

        // Wait for dialog to close with timeout (MAIN THREAD BLOCKS, NOT FX THREAD)
        System.out.println("[JavaFXManager] Waiting for dialog result");
        synchronized (dialogLock) {
            try {
                if (result[0] == null) {
                    dialogLock.wait(timeoutMs);
                    if (result[0] == null) {
                        System.err.println("[JavaFXManager] ERROR: Dialog timeout after " + timeoutMs + "ms");
                    } else {
                        System.out.println("[JavaFXManager] Dialog completed successfully");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        return (T) result[0];
    }

    /**
     * Functional interface for dialog creation.
     * Implementation should create dialog, show it, and set up close handler.
     * MUST NOT BLOCK - should return immediately after showing dialog.
     */
    @FunctionalInterface
    public interface DialogBuilder<T> {
        /**
         * Called on JavaFX thread to create and show dialog.
         * Must set up close handler to notify lock when dialog closes.
         * MUST NOT BLOCK - return immediately after showing dialog.
         *
         * @param resultLock Lock to notify when dialog closes
         * @param resultHolder Array to store result [0] when dialog closes
         */
        void build(Object resultLock, Object[] resultHolder) throws Exception;
    }
}
