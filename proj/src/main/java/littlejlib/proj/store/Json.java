package littlejlib.proj.store;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** The one Gson the store uses, and the atomic file replace. */
public final class Json {

    private Json() { }

    /** Skips the generated {@code _A} base's self-reference; only the record's own fields are stored. */
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return f.getDeclaringClass().getSimpleName().endsWith("_A");
                }

                @Override
                public boolean shouldSkipClass(Class<?> c) {
                    return false;
                }
            })
            .create();

    /** Renames tmp over file. Windows refuses while another program has the file open, so retry briefly. */
    static void replace(Path tmp, Path file) throws IOException {
        for (var attempt = 0; ; attempt++) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (AccessDeniedException e) {
                if (attempt >= 20) throw e;
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
