package littlejlib.proj.cct.db;

import littlejlib.proj.cct.model.HistoryDoc;
import littlejlib.proj.cct.model.ProjectDoc;
import littlejlib.proj.cct.model.SessionDoc;
import littlejlib.proj.store.Table;
import littlejlib.proj.store.Tables;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;

/**
 * The transcript index: files under {@code cache/transcripts}, a cache of {@code ~/.claude/projects}
 * that may be deleted and rebuilt. Any process may read it; indexing is one process at a time.
 */
public final class Db implements Closeable {

    private static Db instance;

    private final Path dir = Tables.cache().resolve("transcripts");
    private final TurnFiles turns = new TurnFiles(dir.resolve("turns"));

    private Db() { }

    public static synchronized Db get() {
        if (instance == null) instance = new Db();
        return instance;
    }

    /** Kept so callers written against the database handle read the same. */
    public Db db() {
        return this;
    }

    public Table<SessionDoc> sessions() {
        return Table.of(dir.resolve("sessions.json"), SessionDoc.class);
    }

    public Table<ProjectDoc> projects() {
        return Table.of(dir.resolve("projects.json"), ProjectDoc.class);
    }

    public Table<HistoryDoc> history() {
        return Table.of(dir.resolve("history.json"), HistoryDoc.class);
    }

    public Path dir() {
        return dir;
    }

    public TurnFiles turns() {
        return turns;
    }

    /** Runs an index pass holding {@code index.lock}, so two processes never index at once. */
    public synchronized <R> R indexing(Supplier<R> work) {
        try {
            Files.createDirectories(dir);
            try (var ch = FileChannel.open(dir.resolve("index.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = ch.lock()) {
                return work.get();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() { }

    public void shutdown() { }
}
