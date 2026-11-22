package littlejlib.proj.cct.ui;

import java.util.List;

import littlejlib.proj.cct.model.*;

/** What one load of a session produced - the session and the turns to paint, together. */
record Loaded(SessionDoc session, List<TurnDoc> turns) { }
