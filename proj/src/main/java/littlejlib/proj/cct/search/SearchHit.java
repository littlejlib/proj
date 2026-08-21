package littlejlib.proj.cct.search;

import littlejlib.proj.cct.model.SessionDoc;

public final class SearchHit {

    public SessionDoc session;

    public int humanMatches;
    public int assistantMatches;
    public int thinkingMatches;
    public boolean cwdMatch;
    public boolean titleMatch;
    public boolean promptMatch;

    public int score;
    public String snippet = "";
    public int snippetLayer;
    public Integer snippetSeq;

    public SearchHit(SessionDoc session) { this.session = session; }

    public int totalMatches() { return humanMatches + assistantMatches + thinkingMatches; }

    public String id() { return session == null ? "" : session.sessionId(); }
}
