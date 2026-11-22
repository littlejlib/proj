package littlejlib.proj.cct.export;

/** One content block of one record, as read. Package-scoped scaffolding for {@link TranscriptReader}. */
final class Block {

    String kind;
    String text;
    String name;
    String input;
    boolean error;

    static Block text(String s) {
        var b = new Block();
        b.kind = "text";
        b.text = s;
        return b;
    }
}
