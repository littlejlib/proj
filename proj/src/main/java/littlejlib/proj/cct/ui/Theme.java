package littlejlib.proj.cct.ui;

import module java.base;

import javafx.scene.Scene;

public final class Theme {

    private Theme() {}

    private static final String CSS = "/littlejlib/proj/cct/light.css";

    public static void applyLight(Scene scene) {
        var url = Theme.class.getResource(CSS);
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
        scene.getRoot().getStyleClass().add("root");
    }
}
