package littlejlib.proj.cct.store;

import module java.base;

public final class PathKey {

    private PathKey() {}

    public static String encode(String cwd) {
        return cwd == null ? null : cwd.replaceAll("[^A-Za-z0-9]", "-");
    }
}
