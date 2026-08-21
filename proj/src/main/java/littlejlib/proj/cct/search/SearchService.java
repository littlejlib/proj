package littlejlib.proj.cct.search;

import module java.base;

import com.arcadedb.database.Database;
import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.Layer;

import static xyz.jphil.arcadedb.datahelper.Query.query;
import static littlejlib.proj.cct.Num.*;
import static littlejlib.proj.cct.model.SessionDoc_A.$sessionId;
import static littlejlib.proj.cct.model.TurnDoc_A.*;

public final class SearchService {

    private final Database db;

    public SearchService(Database db) { this.db = db; }

    public List<SearchHit> find(SearchQuery q) {
        var terms = q.terms();
        if (terms.isEmpty()) return recent(q);
        var hits = new LinkedHashMap<String, SearchHit>();
        matchTurns(q, terms, hits);
        matchSessions(q, terms, hits);
        var out = new ArrayList<SearchHit>(hits.size());
        for (var h : hits.values()) if (Filters.passes(h.session, q)) out.add(h);
        Ranker.rank(out);
        return out.size() > q.limit() ? new ArrayList<>(out.subList(0, q.limit())) : out;
    }

    public List<SearchHit> recent(SearchQuery q) {
        var out = new ArrayList<SearchHit>();
        for (var s : query(db, SessionDoc.TYPEDEF)) if (Filters.passes(s, q)) out.add(new SearchHit(s));
        out.sort(Comparator.comparingLong(h -> -n(h.session.lastActivity())));
        return out.size() > q.limit() ? new ArrayList<>(out.subList(0, q.limit())) : out;
    }

    public SessionDoc session(String sessionId) {
        return query(db, SessionDoc.TYPEDEF).eq($sessionId, sessionId).firstOrNull();
    }

    public List<TurnDoc> turns(String sessionId, int widestLayer) {
        var out = new ArrayList<TurnDoc>();
        for (var t : query(db, TurnDoc.TYPEDEF).eq(TurnDoc_A.$sessionId, sessionId)
                .le($layer, widestLayer).orderByAsc($seq)) out.add(t);
        return out;
    }

    private void matchTurns(SearchQuery q, Terms terms, Map<String, SearchHit> hits) {
        var widest = Math.max(1, Math.min(Layer.TOOLS.depth, q.layer()));
        for (var t : query(db, TurnDoc.TYPEDEF).le($layer, widest).ilike($text, terms.sqlPrefilter())) {
            if (!terms.matches(t.text())) continue;
            var hit = hits.computeIfAbsent(t.sessionId(), this::hitFor);
            if (hit == null) { hits.remove(t.sessionId()); continue; }
            count(hit, t, terms);
        }
    }

    private SearchHit hitFor(String sessionId) {
        var s = session(sessionId);
        return s == null ? null : new SearchHit(s);
    }

    private void count(SearchHit hit, TurnDoc t, Terms terms) {
        var layer = Layer.ofDepth(n(t.layer()));
        switch (layer) {
            case HUMAN -> hit.humanMatches++;
            case ASSISTANT -> hit.assistantMatches++;
            case THINKING -> hit.thinkingMatches++;
            case TOOLS -> { }
        }
        if (hit.snippet.isEmpty() || layer.depth < hit.snippetLayer) {
            hit.snippet = terms.snippet(t.text(), 140);
            hit.snippetLayer = layer.depth;
            hit.snippetSeq = t.seq();
        }
    }

    private void matchSessions(SearchQuery q, Terms terms, Map<String, SearchHit> hits) {
        for (var s : query(db, SessionDoc.TYPEDEF)) {
            var cwd = terms.matches(s.cwd()) || terms.matches(s.projectEncoded());
            var title = terms.matches(s.aiTitle());
            var prompt = terms.matches(s.firstHumanPrompt()) || terms.matches(s.lastPrompt());
            if (!cwd && !title && !prompt) continue;
            var hit = hits.computeIfAbsent(s.sessionId(), k -> new SearchHit(s));
            hit.cwdMatch |= cwd;
            hit.titleMatch |= title;
            hit.promptMatch |= prompt;
            if (hit.snippet.isEmpty()) hit.snippet = snippetOf(s, terms);
        }
    }

    private String snippetOf(SessionDoc s, Terms terms) {
        if (terms.matches(s.cwd())) return s.cwd();
        if (terms.matches(s.aiTitle())) return s.aiTitle();
        if (terms.matches(s.firstHumanPrompt())) return terms.snippet(s.firstHumanPrompt(), 140);
        if (terms.matches(s.lastPrompt())) return terms.snippet(s.lastPrompt(), 140);
        return "";
    }
}
