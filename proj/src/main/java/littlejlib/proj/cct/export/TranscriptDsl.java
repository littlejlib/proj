package littlejlib.proj.cct.export;

import luvml.HtmlAttribute;
import luvml.element.SemanticBlockContainerElement;
import luvml.element.SemanticElementTagNameClassNameMapping.CamelCase_E;
import luvx.Frag_I;

/**
 * The element vocabulary of the semantic-xhtml export, deliberately the SAME one the claude.ai
 * conversation downloader emits - {@code human} / {@code assistant} / {@code metadata} /
 * {@code messages} against the published ccapis-static stylesheet - so one viewer opens a chat
 * pulled from the web api and a claude-code session off this disk without knowing the difference.
 */
public final class TranscriptDsl {

    private TranscriptDsl() {}

    public static HtmlAttribute serialNum(int value) { return new HtmlAttribute("serialNum", String.valueOf(value)); }

    public static HtmlAttribute uuid(String value) { return new HtmlAttribute("uuid", value); }

    public static HtmlAttribute created_at(String value) { return new HtmlAttribute("created_at", value); }

    public static HtmlAttribute speaker(String value) { return new HtmlAttribute("speaker", value); }

    public static Human_E human(Frag_I<?>... f) { return new Human_E(f); }

    public static Assistant_E assistant(Frag_I<?>... f) { return new Assistant_E(f); }

    public static Metadata_E metadata(Frag_I<?>... f) { return new Metadata_E(f); }

    public static Messages_E messages(Frag_I<?>... f) { return new Messages_E(f); }

    public static class Human_E extends SemanticBlockContainerElement<Human_E> implements CamelCase_E {
        public Human_E(Frag_I<?>... f) { super(Human_E.class); ____(f); }
    }

    public static class Assistant_E extends SemanticBlockContainerElement<Assistant_E> implements CamelCase_E {
        public Assistant_E(Frag_I<?>... f) { super(Assistant_E.class); ____(f); }
    }

    public static class Metadata_E extends SemanticBlockContainerElement<Metadata_E> implements CamelCase_E {
        public Metadata_E(Frag_I<?>... f) { super(Metadata_E.class); ____(f); }
    }

    public static class Messages_E extends SemanticBlockContainerElement<Messages_E> implements CamelCase_E {
        public Messages_E(Frag_I<?>... f) { super(Messages_E.class); ____(f); }
    }
}
