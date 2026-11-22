package littlejlib.proj.track;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A sibling {@code <file>.lock} created exclusively. Several sessions write the same files; this makes
 * each write (and an id taken before it) one at a time, on a local disk and on a network drive alike.
 */
public final class FileLock implements AutoCloseable {

    static final long WAIT_MS = 10_000, STALE_MS = 30_000;

    final Path lock;

    private FileLock(Path lock) {
        this.lock = lock;
    }

    public static FileLock on(Path file) throws IOException {
        var lock = file.resolveSibling(file.getFileName() + ".lock");
        var deadline = System.currentTimeMillis() + WAIT_MS;
        while (true) {
            try {
                Files.createFile(lock);
                return new FileLock(lock);
            } catch (FileAlreadyExistsException | AccessDeniedException e) {
                if (stale(lock)) {
                    Files.deleteIfExists(lock);
                    continue;
                }
                if (System.currentTimeMillis() > deadline) throw new IOException("busy, another writer holds " + lock);
                pause();
            }
        }
    }

    static boolean stale(Path lock) {
        try {
            return System.currentTimeMillis() - Files.getLastModifiedTime(lock).toMillis() > STALE_MS;
        } catch (IOException e) {
            return false;
        }
    }

    static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(lock);
    }
}
