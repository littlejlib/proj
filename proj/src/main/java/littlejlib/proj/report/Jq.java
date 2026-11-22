package littlejlib.proj.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

import java.util.ArrayList;
import java.util.List;

/** A jq program over a json text, like {@code gh --jq}: strings come out raw, everything else as compact json. */
public final class Jq {

    private Jq() { }

    static final ObjectMapper MAPPER = new ObjectMapper();
    static Scope root;

    static synchronized Scope root() {
        if (root == null) {
            root = Scope.newEmptyScope();
            BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, root);
        }
        return root;
    }

    public static List<String> run(String json, String program) {
        try {
            var in = MAPPER.readTree(json);
            var q = JsonQuery.compile(program, Versions.JQ_1_6);
            var out = new ArrayList<String>();
            q.apply(Scope.newChildScope(root()), in, (JsonNode n) -> out.add(n.isTextual() ? n.asText() : n.toString()));
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("jq: " + e.getMessage(), e);
        }
    }
}
