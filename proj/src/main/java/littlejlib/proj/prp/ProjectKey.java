package littlejlib.proj.prp;

import datapotter.arcadedbhelper.ArcadeData;
import datapotter.arcadedbhelper.TypeDef;

/**
 * A project's short name - its KEY, in the sense JIRA means it.
 * <p>
 * {@code billing} plus PRP {@code 38} is {@code billing-38}, and that pair behaves exactly like a
 * ticket number: it is short, it is stable, it survives being said out loud, and it identifies one
 * piece of work across every project on the machine. The tab name is that ticket with the task
 * appended - {@code billing-38-r2} - so the identifier is a PREFIX of the name, not a separate idea.
 * <p>
 * It is stored per project rather than derived because it cannot be derived: {@code billing} appears
 * nowhere in that project's path. Set once, kept; lost with the index, and re-typed in a moment.
 */
@ArcadeData
public final class ProjectKey extends ProjectKey_A {

    String path;            // absolute project root, and the identity of the row
    String key;
    Long updatedAt;

    public static final TypeDef<ProjectKey> TYPEDEF =
            schemaBuilder()
                    .factory(ProjectKey::new)
                    .unique($path)
                    .__();

    @Override
    public String toString() {
        return key + "  " + path;
    }
}
