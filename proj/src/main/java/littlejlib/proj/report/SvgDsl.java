package littlejlib.proj.report;

import luvml.HtmlAttribute;
import luvml.element.SemanticBlockContainerElement;
import luvml.element.SemanticElementTagNameClassNameMapping.LowerCase_E;
import luvx.Frag_I;

/** The few SVG elements the graph needs, as luvml elements (luvml itself has no SVG vocabulary). */
public final class SvgDsl {

    private SvgDsl() { }

    public static HtmlAttribute at(String name, Object value) {
        return new HtmlAttribute(name, String.valueOf(value));
    }

    public static Svg_E svg(Frag_I<?>... f) { return new Svg_E(f); }

    public static G_E g(Frag_I<?>... f) { return new G_E(f); }

    public static Rect_E rect(Frag_I<?>... f) { return new Rect_E(f); }

    public static Path_E path(Frag_I<?>... f) { return new Path_E(f); }

    public static Text_E text(Frag_I<?>... f) { return new Text_E(f); }

    public static Title_E title(Frag_I<?>... f) { return new Title_E(f); }

    public static class Svg_E extends SemanticBlockContainerElement<Svg_E> implements LowerCase_E {
        public Svg_E(Frag_I<?>... f) { super(Svg_E.class); ____(f); }
    }

    public static class G_E extends SemanticBlockContainerElement<G_E> implements LowerCase_E {
        public G_E(Frag_I<?>... f) { super(G_E.class); ____(f); }
    }

    public static class Rect_E extends SemanticBlockContainerElement<Rect_E> implements LowerCase_E {
        public Rect_E(Frag_I<?>... f) { super(Rect_E.class); ____(f); }
    }

    public static class Path_E extends SemanticBlockContainerElement<Path_E> implements LowerCase_E {
        public Path_E(Frag_I<?>... f) { super(Path_E.class); ____(f); }
    }

    public static class Text_E extends SemanticBlockContainerElement<Text_E> implements LowerCase_E {
        public Text_E(Frag_I<?>... f) { super(Text_E.class); ____(f); }
    }

    public static class Title_E extends SemanticBlockContainerElement<Title_E> implements LowerCase_E {
        public Title_E(Frag_I<?>... f) { super(Title_E.class); ____(f); }
    }
}
