package littlejlib.proj.cct.model;

import xyz.jphil.arcadedb.datahelper.*;

@ArcadeData
public final class TurnDoc extends TurnDoc_A {

    String turnKey;
    String sessionId;
    String uuid;
    String parentUuid;

    Integer seq;
    Integer layer;
    String role;
    String text;

    Long timestamp;
    Boolean sidechain;
    String agentId;
    String model;
    String effort;

    public static final TypeDef<TurnDoc> TYPEDEF =
        schemaBuilder()
            .factory(TurnDoc::new)
            .unique($turnKey)
            .lsmIndex($sessionId)
            .lsmIndex($layer)
            .__();
}
