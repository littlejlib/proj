package littlejlib.proj.cct.model;

import datapotter.datahelper.Data;

@Data
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

}
