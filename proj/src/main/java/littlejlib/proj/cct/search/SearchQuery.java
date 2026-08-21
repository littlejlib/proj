package littlejlib.proj.cct.search;

import xyz.jphil.datahelper.Data;

@Data
public final class SearchQuery extends SearchQuery_A {

    String text = "";
    int layer = 1;
    String mode = "auto";

    String projectEncoded;
    String cwdContains;
    Long fromMillis;
    Long toMillis;
    String model;
    String effort;
    String gitBranch;
    String permissionMode;
    String sessionKind;
    Boolean sidechainOnly;
    Boolean subagentsOnly;
    Long minFileBytes;
    Long minTotalTokens;

    int limit = 60;

    public MatchMode matchMode() { return MatchMode.of(mode); }

    public Terms terms() { return Terms.of(text, matchMode()); }
}
