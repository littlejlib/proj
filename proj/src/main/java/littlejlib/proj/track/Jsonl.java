package littlejlib.proj.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/** One record per line, appended, never rewritten. A line that is not json is skipped with a note, never fatal. */
public final class Jsonl {

    private Jsonl() { }

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public interface Action<T> {
        T run() throws IOException;
    }

    public static <T> T locked(Path file, Action<T> action) throws IOException {
        try (var l = FileLock.on(file)) {
            return action.run();
        }
    }

    public static void append(Path file, Object record) throws IOException {
        locked(file, () -> {
            write(file, record);
            return null;
        });
    }

    /** Append without taking the lock: for callers already inside {@link #locked}. */
    public static void write(Path file, Object record) throws IOException {
        Files.writeString(file, GSON.toJson(record) + "\n", StandardCharsets.UTF_8, CREATE, APPEND);
    }

    public static <T> List<T> read(Path file, Class<T> type) throws IOException {
        var out = new ArrayList<T>();
        if (!Files.isRegularFile(file)) return out;
        var n = 0;
        for (var line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            n++;
            if (line.isBlank()) continue;
            try {
                var r = GSON.fromJson(line, type);
                if (r != null) out.add(r);
            } catch (JsonParseException e) {
                System.err.println(file.getFileName() + ":" + n + ": skipped, not a json line");
            }
        }
        return out;
    }

    public static <T> T copy(T record, Class<T> type) {
        return GSON.fromJson(GSON.toJson(record), type);
    }
}
