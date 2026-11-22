package littlejlib.proj.cct.ui;

import module java.base;

import com.arcadedb.database.Database;
import javafx.application.Platform;
import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.search.SearchService;

public final class DbWorker {

    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "cct-db");
        t.setDaemon(true);
        return t;
    });

    private volatile Db handle;
    private volatile SearchService svc;

    public void open(Runnable onReady, Consumer<String> onError) {
        pool.submit(() -> {
            try {
                if (handle == null) {
                    handle = Db.get();
                    svc = new SearchService(handle.db());
                }
                Platform.runLater(onReady);
            } catch (Exception e) {
                var msg = String.valueOf(e.getMessage());
                Platform.runLater(() -> onError.accept(msg));
            }
        });
    }

    public <T> void run(Supplier<T> job, Consumer<T> onDone, Consumer<String> onError) {
        pool.submit(() -> {
            try {
                var result = job.get();
                Platform.runLater(() -> onDone.accept(result));
            } catch (Exception e) {
                var msg = String.valueOf(e.getMessage());
                Platform.runLater(() -> onError.accept(msg));
            }
        });
    }

    public SearchService search() { return svc; }

    public Database database() { return handle == null ? null : handle.db(); }

    public void release() {
        pool.submit(() -> {
            if (handle != null) handle.close();
            handle = null;
            svc = null;
        });
    }

    public void close() {
        release();
        pool.shutdown();
    }
}
