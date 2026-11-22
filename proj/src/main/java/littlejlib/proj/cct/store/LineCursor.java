package littlejlib.proj.cct.store;

import module java.base;

import static java.nio.file.StandardOpenOption.READ;

public final class LineCursor implements Closeable {

    private final FileChannel channel;
    private final InputStream in;
    private final byte[] chunk = new byte[1 << 16];
    private int chunkLen, chunkPos;
    private byte[] line = new byte[1 << 16];
    private int lineLen;
    private long offset;

    public LineCursor(Path file, long startOffset) throws IOException {
        channel = FileChannel.open(file, READ);
        channel.position(startOffset);
        in = new BufferedInputStream(Channels.newInputStream(channel), 1 << 16);
        offset = startOffset;
    }

    public long offset() { return offset; }

    public byte[] buffer() { return line; }

    public int length() { return lineLen; }

    public boolean next() throws IOException {
        lineLen = 0;
        var consumed = 0L;
        while (true) {
            if (chunkPos >= chunkLen) {
                chunkLen = in.read(chunk);
                chunkPos = 0;
                if (chunkLen <= 0) return false;
            }
            var nl = -1;
            for (var i = chunkPos; i < chunkLen; i++) if (chunk[i] == '\n') { nl = i; break; }
            if (nl < 0) {
                append(chunkPos, chunkLen - chunkPos);
                consumed += chunkLen - chunkPos;
                chunkPos = chunkLen;
            } else {
                append(chunkPos, nl - chunkPos);
                consumed += (nl - chunkPos) + 1;
                chunkPos = nl + 1;
                offset += consumed;
                trimCarriageReturn();
                return true;
            }
        }
    }

    private void trimCarriageReturn() {
        if (lineLen > 0 && line[lineLen - 1] == '\r') lineLen--;
    }

    private void append(int from, int count) {
        if (count <= 0) return;
        if (lineLen + count > line.length) {
            var grown = Math.max(line.length * 2, lineLen + count);
            line = Arrays.copyOf(line, grown);
        }
        System.arraycopy(chunk, from, line, lineLen, count);
        lineLen += count;
    }

    @Override
    public void close() throws IOException {
        in.close();
        channel.close();
    }
}
