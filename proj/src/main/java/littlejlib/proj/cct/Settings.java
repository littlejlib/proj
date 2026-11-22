package littlejlib.proj.cct;

import datapotter.datahelper.Data;

@Data
public final class Settings extends Settings_A {

    String terminalCommand = "";
    int defaultLayer = 1;
    int resultLimit = 60;
    boolean includeThinking = true;
    boolean includeAssistant = true;
    int snippetRadius = 140;

    public static Settings defaults() { return new Settings(); }
}
