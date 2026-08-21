package littlejlib.proj.cct.store;

import module java.base;

import com.fasterxml.jackson.core.*;

public final class RecordReader {

    private RecordReader() {}

    static final JsonFactory FACTORY = JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder()
            .maxStringLength(Integer.MAX_VALUE)
            .maxNestingDepth(2000)
            .build())
        .build();

    public static RawRecord read(byte[] buf, int len) {
        if (len <= 1) return null;
        var r = new RawRecord().lineBytes(len);
        try (var p = FACTORY.createParser(buf, 0, len)) {
            if (p.nextToken() != JsonToken.START_OBJECT) return null;
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                var f = p.currentName();
                p.nextToken();
                field(p, r, f);
            }
        } catch (Exception e) {
            return r.type() == null ? null : r;
        }
        return r;
    }

    private static void field(JsonParser p, RawRecord r, String f) throws IOException {
        switch (f) {
            case "type" -> r.type(str(p));
            case "uuid" -> r.uuid(str(p));
            case "parentUuid" -> r.parentUuid(str(p));
            case "timestamp" -> stamp(p, r);
            case "cwd" -> r.cwd(str(p));
            case "sessionId" -> r.sessionId(str(p));
            case "version" -> r.version(str(p));
            case "gitBranch" -> r.gitBranch(str(p));
            case "userType" -> r.userType(str(p));
            case "entrypoint" -> r.entrypoint(str(p));
            case "sessionKind" -> r.sessionKind(str(p));
            case "permissionMode", "mode" -> r.permissionMode(str(p));
            case "promptSource" -> r.promptSource(str(p));
            case "isSidechain" -> r.sidechain(p.getCurrentToken() == JsonToken.VALUE_TRUE);
            case "agentId" -> r.agentId(str(p));
            case "effort" -> r.effort(str(p));
            case "aiTitle" -> r.aiTitle(str(p));
            case "lastPrompt" -> r.lastPrompt(str(p));
            case "agentName", "name" -> r.agentName(str(p));
            case "operation" -> r.operation(str(p));
            case "content" -> content(p, r);
            case "subtype" -> r.systemSubtype(str(p));
            case "display" -> r.display(str(p));
            case "project" -> r.project(str(p));
            case "messageCount" -> r.messageCount(intOf(p));
            case "origin" -> origin(p, r);
            case "message" -> MessageReader.read(p, r);
            default -> p.skipChildren();
        }
    }

    private static void content(JsonParser p, RawRecord r) throws IOException {
        if (p.getCurrentToken() == JsonToken.VALUE_STRING) r.content(p.getText());
        else p.skipChildren();
    }

    private static void origin(JsonParser p, RawRecord r) throws IOException {
        if (p.getCurrentToken() != JsonToken.START_OBJECT) { p.skipChildren(); return; }
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            var f = p.currentName();
            p.nextToken();
            if ("kind".equals(f)) r.originKind(str(p)); else p.skipChildren();
        }
    }

    private static void stamp(JsonParser p, RawRecord r) throws IOException {
        if (p.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
            r.timestampMillis(p.getLongValue());
            return;
        }
        var s = str(p);
        r.timestampIso(s);
        r.timestampMillis(Timestamps.millis(s));
    }

    static String str(JsonParser p) throws IOException {
        return p.getCurrentToken() == JsonToken.VALUE_STRING ? p.getText() : null;
    }

    static int intOf(JsonParser p) throws IOException {
        return p.getCurrentToken() == JsonToken.VALUE_NUMBER_INT ? p.getIntValue() : 0;
    }

    static long longOf(JsonParser p) throws IOException {
        return p.getCurrentToken() == JsonToken.VALUE_NUMBER_INT ? p.getLongValue() : 0L;
    }
}
