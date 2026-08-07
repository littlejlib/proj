package littlejlib.proj.daemon;

import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * One JSON object per line, over loopback. Newlines inside strings are escaped by JSON, so a line
 * is a whole message and no length prefix is needed.
 */
public final class Protocol {

    private Protocol() { }

    static final Gson GSON = new Gson();

    public static void write(OutputStream out, Object message) throws IOException {
        var writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write(GSON.toJson(message));
        writer.write('\n');
        writer.flush();
    }

    public static Request readRequest(InputStream in) throws IOException {
        return read(in, Request.class);
    }

    public static Response readResponse(InputStream in) throws IOException {
        return read(in, Response.class);
    }

    static <T> T read(InputStream in, Class<T> type) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        var line = reader.readLine();
        if (line == null) throw new EOFException("connection closed before a message arrived");
        return GSON.fromJson(line, type);
    }
}
