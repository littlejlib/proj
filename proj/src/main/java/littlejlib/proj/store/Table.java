package littlejlib.proj.store;

import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One JSON file of rows, held in memory. Reads never lock: they reload when the file changed on disk.
 * A write locks {@code <file>.lock} (across processes), re-reads the file, applies the change and
 * renames a temp file over it, so the daemon and a CLI can both write without losing each other's rows.
 * Rows handed out are shared: change them only inside {@link #write}.
 */
public final class Table<T> {

    private static final Map<Path, Table<?>> OPEN = new ConcurrentHashMap<>();

    private final Path file;
    private final Type listType;
    private boolean held = true;
    private List<T> rows = List.of();
    private FileTime seenTime;
    private long seenSize = -1;

    private Table(Path file, Class<T> type) {
        this.file = file;
        this.listType = TypeToken.getParameterized(List.class, type).getType();
    }

    /** One instance per file in this process, so the in-process lock and the cache are shared. */
    @SuppressWarnings("unchecked")
    public static <T> Table<T> of(Path file, Class<T> type) {
        return (Table<T>) OPEN.computeIfAbsent(file.toAbsolutePath().normalize(), f -> new Table<>(f, type));
    }

    /** Not kept in memory between reads: for a big table that a resident process rarely asks for. */
    public Table<T> unheld() {
        held = false;
        return this;
    }

    public Path file() {
        return file;
    }

    public synchronized List<T> all() {
        refresh();
        var out = rows;
        if (!held) forget();
        return out;
    }

    private void forget() {
        rows = List.of();
        seenTime = null;
        seenSize = -1;
    }

    public List<T> where(Predicate<? super T> p) {
        return all().stream().filter(p).toList();
    }

    public T first(Predicate<? super T> p) {
        for (var r : all()) if (p.test(r)) return r;
        return null;
    }

    public boolean any(Predicate<? super T> p) {
        return first(p) != null;
    }

    public int size() {
        return all().size();
    }

    /** Applies a change to the rows as they are on disk now, and saves them. */
    public synchronized <R> R write(Function<List<T>, R> change) {
        try {
            Files.createDirectories(file.getParent());
            try (var ch = FileChannel.open(lockFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = ch.lock()) {
                var fresh = new ArrayList<T>(read(false));
                var result = change.apply(fresh);
                save(fresh);
                if (held) {
                    rows = List.copyOf(fresh);
                    stamp();
                } else forget();
                return result;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    public void update(Consumer<List<T>> change) {
        write(rows -> {
            change.accept(rows);
            return null;
        });
    }

    public void replaceAll(Collection<? extends T> all) {
        update(rows -> {
            rows.clear();
            rows.addAll(all);
        });
    }

    /** Deletes the file; the next read is empty. For a cache being rebuilt. */
    public synchronized void drop() {
        try {
            Files.deleteIfExists(file);
            rows = List.of();
            seenTime = null;
            seenSize = -1;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path lockFile() {
        return file.resolveSibling(file.getFileName() + ".lock");
    }

    private void refresh() {
        try {
            var time = Files.getLastModifiedTime(file);
            var size = Files.size(file);
            if (time.equals(seenTime) && size == seenSize) return;
            rows = List.copyOf(read(true));
            seenTime = time;
            seenSize = size;
        } catch (NoSuchFileException e) {
            rows = List.of();
            seenTime = null;
            seenSize = -1;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private List<T> read(boolean lenient) throws IOException {
        try (var r = Files.newBufferedReader(file)) {
            List<T> list = Json.GSON.fromJson(r, listType);
            if (list == null) return List.of();
            list.removeIf(java.util.Objects::isNull);
            return list;
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (com.google.gson.JsonParseException e) {
            if (!lenient) throw new IOException("unreadable " + file + " (not overwritten): " + e.getMessage(), e);
            // A cache file that does not parse is rebuilt by the next scan; never fatal.
            System.err.println("[store] ignoring unreadable " + file + ": " + e.getMessage());
            return List.of();
        }
    }

    private void save(List<T> list) throws IOException {
        var tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var w = Files.newBufferedWriter(tmp)) {
            Json.GSON.toJson(list, listType, w);
        }
        Json.replace(tmp, file);
    }

    private void stamp() throws IOException {
        seenTime = Files.getLastModifiedTime(file);
        seenSize = Files.size(file);
    }
}
