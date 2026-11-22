package littlejlib.proj.migrate;

import datapotter.datahelper.Data;

/** One line of {@code prp/renumber-map.jsonl}; kind is main, data, sub or log. */
@Data
public final class MapEntry extends MapEntry_A {

    String oldFile;
    String newFile;
    String oldNumber;
    String newNumber;
    String kind;
    String uuid;

    public boolean main() {
        return "main".equals(kind);
    }
}
