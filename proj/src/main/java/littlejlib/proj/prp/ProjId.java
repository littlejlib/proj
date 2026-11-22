package littlejlib.proj.prp;

/**
 * A project's id, in {@link Base34} like every id of proj. Ids minted before 2026-11-21 are 28 letters
 * a-z (base26); they stay valid and unchanged, since an id is only useful while it does not change.
 */
public final class ProjId {

    private ProjId() { }

    public static String mint() {
        return Base34.mint();
    }

    public static boolean valid(String s) {
        return Base34.valid(s) || legacy(s);
    }

    /** The base26, 28 letter form of the first versions. */
    public static boolean legacy(String s) {
        return s != null && s.matches("[a-z]{28}");
    }
}
