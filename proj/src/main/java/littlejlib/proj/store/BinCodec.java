package littlejlib.proj.store;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cache tables' file format: every distinct string once, sorted and front-coded (paths share their
 * folders), then one column per field (strings as pool indexes, numbers as varints, longs as the difference
 * from the previous row). A table of 120,000 file rows is about a fifth of its JSON and reads several times
 * faster. Fields are the record's own non-static, non-transient fields, matched by NAME on read, so adding or
 * dropping a field keeps old files readable. {@link #toJson} shows any file as JSON.
 */
public final class BinCodec<T> {

    static final int MAGIC = 0x504A5431; // "PJT1"

    enum Kind { STRING, INT, INT_BOX, LONG, LONG_BOX, BOOL, BOOL_BOX }

    record Col(Field field, Kind kind) { }

    private final Constructor<T> ctor;
    private final List<Col> cols = new ArrayList<>();

    public BinCodec(Class<T> type) {
        try {
            ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(type + " needs a no-argument constructor", e);
        }
        for (var f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) continue;
            f.setAccessible(true);
            cols.add(new Col(f, kindOf(f.getType())));
        }
    }

    static Kind kindOf(Class<?> c) {
        if (c == String.class) return Kind.STRING;
        if (c == int.class) return Kind.INT;
        if (c == Integer.class) return Kind.INT_BOX;
        if (c == long.class) return Kind.LONG;
        if (c == Long.class) return Kind.LONG_BOX;
        if (c == boolean.class) return Kind.BOOL;
        if (c == Boolean.class) return Kind.BOOL_BOX;
        throw new IllegalArgumentException("no binary column for " + c);
    }

    // ---------- write ----------

    public void write(List<T> rows, OutputStream os) throws IOException {
        var out = new DataOutputStream(new BufferedOutputStream(os, 1 << 16));
        try {
            var unique = new java.util.HashSet<String>();
            for (var r : rows)
                for (var c : cols)
                    if (c.kind == Kind.STRING) {
                        var s = (String) c.field.get(r);
                        if (s != null) unique.add(s);
                    }
            var strings = unique.toArray(new String[0]);
            java.util.Arrays.sort(strings);
            var index = new HashMap<String, Integer>(strings.length * 2);
            out.writeInt(MAGIC);
            writeVar(out, cols.size());
            for (var c : cols) {
                writeStr(out, c.field.getName());
                out.writeByte(c.kind.ordinal());
            }
            writeVar(out, strings.length);
            var prev = "";
            var i = 0;
            for (var s : strings) {
                var shared = shared(prev, s);
                writeVar(out, shared);
                writeStr(out, s.substring(shared));
                index.put(s, i++);
                prev = s;
            }
            writeVar(out, rows.size());
            for (var c : cols) {
                var last = 0L;
                for (var r : rows) {
                    var v = c.field.get(r);
                    switch (c.kind) {
                        case STRING -> writeVar(out, v == null ? 0 : index.get((String) v) + 1L);
                        case INT, INT_BOX, LONG, LONG_BOX -> {
                            if (v == null) { out.writeByte(0); continue; }
                            var n = ((Number) v).longValue();
                            if (c.kind == Kind.INT_BOX || c.kind == Kind.LONG_BOX) out.writeByte(1);
                            var delta = c.kind == Kind.LONG || c.kind == Kind.LONG_BOX;
                            writeVar(out, zig(delta ? n - last : n));
                            if (delta) last = n;
                        }
                        case BOOL, BOOL_BOX -> out.writeByte(v == null ? 2 : ((Boolean) v) ? 1 : 0);
                    }
                }
            }
            out.flush();
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        }
    }

    // ---------- read ----------

    public List<T> read(InputStream is) throws IOException {
        var in = new DataInputStream(new BufferedInputStream(is, 1 << 16));
        if (in.readInt() != MAGIC) throw new IOException("not a proj table file");
        var fieldCount = (int) readVar(in);
        var names = new String[fieldCount];
        var kinds = new Kind[fieldCount];
        for (var i = 0; i < fieldCount; i++) {
            names[i] = readStr(in);
            kinds[i] = Kind.values()[in.readUnsignedByte()];
        }
        var pool = new String[(int) readVar(in)];
        var prev = "";
        for (var i = 0; i < pool.length; i++) {
            var shared = (int) readVar(in);
            prev = pool[i] = prev.substring(0, shared) + readStr(in);
        }
        var n = (int) readVar(in);
        var rows = new ArrayList<T>(n);
        try {
            for (var i = 0; i < n; i++) rows.add(ctor.newInstance());
            Map<String, Col> byName = new HashMap<>();
            for (var c : cols) byName.put(c.field.getName(), c);
            for (var f = 0; f < fieldCount; f++) {
                var target = byName.get(names[f]);
                if (target != null && target.kind != kinds[f]) target = null; // type changed: leave the default
                var last = 0L;
                for (var r = 0; r < n; r++) {
                    Object v;
                    switch (kinds[f]) {
                        case STRING -> {
                            var k = readVar(in);
                            v = k == 0 ? null : pool[(int) (k - 1)];
                        }
                        case INT, INT_BOX, LONG, LONG_BOX -> {
                            var boxed = kinds[f] == Kind.INT_BOX || kinds[f] == Kind.LONG_BOX;
                            if (boxed && in.readUnsignedByte() == 0) { v = null; break; }
                            var x = unzig(readVar(in));
                            if (kinds[f] == Kind.LONG || kinds[f] == Kind.LONG_BOX) { x += last; last = x; }
                            v = kinds[f] == Kind.INT || kinds[f] == Kind.INT_BOX ? (Object) (int) x : (Object) x;
                        }
                        default -> {
                            var b = in.readUnsignedByte();
                            v = b == 2 ? null : b == 1;
                        }
                    }
                    if (target != null && (v != null || !target.field.getType().isPrimitive()))
                        target.field.set(rows.get(r), v);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        return rows;
    }

    /** Any table file as JSON, field order as stored. */
    public static String toJson(InputStream is) throws IOException {
        var in = new DataInputStream(new BufferedInputStream(is, 1 << 16));
        if (in.readInt() != MAGIC) throw new IOException("not a proj table file");
        var fieldCount = (int) readVar(in);
        var names = new String[fieldCount];
        var kinds = new Kind[fieldCount];
        for (var i = 0; i < fieldCount; i++) {
            names[i] = readStr(in);
            kinds[i] = Kind.values()[in.readUnsignedByte()];
        }
        var pool = new String[(int) readVar(in)];
        var prev = "";
        for (var i = 0; i < pool.length; i++) {
            var shared = (int) readVar(in);
            prev = pool[i] = prev.substring(0, shared) + readStr(in);
        }
        var n = (int) readVar(in);
        var rows = new ArrayList<Map<String, Object>>(n);
        for (var i = 0; i < n; i++) rows.add(new java.util.LinkedHashMap<>());
        for (var f = 0; f < fieldCount; f++) {
            var last = 0L;
            for (var r = 0; r < n; r++) {
                Object v;
                switch (kinds[f]) {
                    case STRING -> { var k = readVar(in); v = k == 0 ? null : pool[(int) (k - 1)]; }
                    case INT, INT_BOX, LONG, LONG_BOX -> {
                        var boxed = kinds[f] == Kind.INT_BOX || kinds[f] == Kind.LONG_BOX;
                        if (boxed && in.readUnsignedByte() == 0) { v = null; break; }
                        var x = unzig(readVar(in));
                        if (kinds[f] == Kind.LONG || kinds[f] == Kind.LONG_BOX) { x += last; last = x; }
                        v = x;
                    }
                    default -> { var b = in.readUnsignedByte(); v = b == 2 ? null : b == 1; }
                }
                if (v != null) rows.get(r).put(names[f], v);
            }
        }
        return new com.google.gson.GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(rows);
    }

    // ---------- primitives ----------

    static int shared(String a, String b) {
        var max = Math.min(a.length(), b.length());
        var i = 0;
        while (i < max && a.charAt(i) == b.charAt(i)) i++;
        if (i > 0 && Character.isHighSurrogate(a.charAt(i - 1))) i--; // never split a surrogate pair
        return i;
    }

    static long zig(long v) { return (v << 1) ^ (v >> 63); }

    static long unzig(long v) { return (v >>> 1) ^ -(v & 1); }

    static void writeVar(DataOutputStream out, long v) throws IOException {
        while ((v & ~0x7FL) != 0) {
            out.writeByte((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.writeByte((int) v);
    }

    static long readVar(DataInputStream in) throws IOException {
        long v = 0;
        for (var shift = 0; shift < 64; shift += 7) {
            var b = in.read();
            if (b < 0) throw new EOFException();
            v |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return v;
        }
        throw new IOException("bad varint");
    }

    static void writeStr(DataOutputStream out, String s) throws IOException {
        var b = s.getBytes(StandardCharsets.UTF_8);
        writeVar(out, b.length);
        out.write(b);
    }

    static String readStr(DataInputStream in) throws IOException {
        var b = new byte[(int) readVar(in)];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
