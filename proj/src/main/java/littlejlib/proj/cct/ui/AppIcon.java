package littlejlib.proj.cct.ui;

import module java.base;

import javafx.scene.image.Image;

public final class AppIcon {

    private AppIcon() {}

    private static final String BASE = "/littlejlib/proj/cct/icon/ccfind-";
    private static final int[] PX = {16, 32, 64, 128, 256};

    private static List<Image> cached;

    public static synchronized List<Image> sizes() {
        if (cached == null) {
            var out = new ArrayList<Image>();
            for (var px : PX) {
                var img = load(px);
                if (img != null) out.add(img);
            }
            cached = List.copyOf(out);
        }
        return cached;
    }

    public static Image at(int px) {
        var img = load(px);
        if (img != null) return img;
        var all = sizes();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    public static InputStream stream(int px) {
        return AppIcon.class.getResourceAsStream(BASE + px + ".png");
    }

    private static Image load(int px) {
        try (var in = stream(px)) {
            return in == null ? null : new Image(in);
        } catch (IOException e) {
            return null;
        }
    }
}
