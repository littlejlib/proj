# Introduction (Obsolete) - Project Build & Dev Misc Tools

    Command-line tool for managing development projects with AI template support.

    ## Features

    - **Project Management**: Quick navigation and opening of projects across organizations
    - **Git Integration**: Clone repositories with automatic directory structure
    - **PRP Management**: Built-in support for Project Requirement Prompts (PRPs)
    - **JavaFX UI**: Modern graphical interface for PRP management

    ## PRP Templates

    This repository hosts PRP (Project Requirement Prompts) templates for use with AI coding assistants.

    ### Available Templates

    - [PRPTemplate-v2026-01-29_2045Z-klwXbO.md](prp-templates/PRPTemplate-v2026-01-29_2045Z-klwXbO.md) - Current default template (UTC timestamp, CRC32 verified)

    ### Using Templates

    You can reference these templates in your `Settings.xml`:

    ```xml
    <prpTemplate src="https://littlejlib.github.io/proj/prp-templates/PRPTemplate-v2026-01-29_2045Z-klwXbO.md" />
    ```

    The tool will automatically download and cache the template locally for offline use.

    ## Documentation

    See the [README](https://github.com/littlejlib/proj) for full documentation.

    ## About PRP Convention

    The PRP (Project Requirement Prompts) convention is based on work by Cole Medin. See his video: [Project Requirement Prompts](https://www.youtube.com/watch?v=Mk87sFlUG28).

    ## Repository

    Visit the [GitHub repository](https://github.com/littlejlib/proj) for source code and releases.


---

# proj - notes that must survive a refactor

## Keyboard navigation in the picker window - DO NOT LOSE THIS

The arrow keys move between the two panes and within one, and the rule is spatial with no exceptions: **the arrow that points out of a pane leaves it.**

- Projects table is the LEFT pane: **Right** opens the PRP panel and focuses it (opening it if closed). Left is left alone and still collapses a tree node.
- PRP panel is the RIGHT pane: **Left** returns to the projects table. Right is left alone and still expands a tree node.
- Both panes have the same vertical shape: the filter box is the top of the pane, **Down** from the filter enters the list, **Up** off the top of the list returns to the filter. So the filter is always one Up away, in either pane.
- Typing a letter anywhere in a pane goes to that pane's filter, with the focus.
- Entering a pane always lands on its FILTER, never its list - so Down is always the step from filter into list.

Consequence, accepted deliberately: Right no longer expands a node in the projects tree. `ProjectTreeBuilder` expands every node it builds, so nothing is collapsed unless the user collapsed it.

Implemented in `ProjectPickerDialog.onArrowKey` and `PrpPanel.onKey`. Requirement is in `prp/27-prp-prp_side_panel_and_sorting_order.md`.

## The rest of the keyboard model

Function keys are the actions, with no modifier, handled at the scene so they work from any focus owner. Letters and digits belong to whatever is being typed and may never be shortcuts - see `$claudeprompts/guidelines.keyboard-shortcuts.md`, and `ui/Keys` for why a JavaFX mnemonic cannot be trusted. Alt+digit switches tabs - **Alt+1 Projects, Alt+2 Recent PRPs, Alt+3 Transcripts, Alt+4 Settings** - and Alt+letter works the view toggles.

The PRP panel binds the SAME function keys as the projects table, to different actions. That is resolved by FOCUS: the focused pane's legend is drawn at full strength and the other fades, and the focused panel's header goes blue.

The Transcripts tab is a THIRD keyboard model, in `cct/ui/Actions`, and the tab that has the selection owns the keys - `ProjectPickerDialog.onGlobalKey` and `onAltKey` route to whichever tab is showing. Two things about it are worth not rediscovering:

- **F11 is the action menu on BOTH tabs.** F1-F12 ran out: the flat one-key-per-action scheme could not absorb export, find-in-transcript, select-all, open-the-PRP, show-in-projects and a shell selector. Those live in the F11 menu with their key printed beside them, which is a better permanent legend than a status bar that no longer fits. Alt+letter accelerators exist for them but are the second way in, not the first.
- **Esc must be allowed to fall through.** `Actions.back()` returns false once there is nowhere left to go inside the tab, and that is what lets Esc leave the tab instead of being a key that does nothing forever. A handler that always consumes Esc traps the user.

## The transcripts half

`littlejlib.proj.cct` is the old `ccsearchconv` / `ccfind.exe`, merged in under PRP 29. Three things about it are load-bearing and easy to undo by accident:

- **The index is a SECOND DATABASE inside proj's ArcadeDB server** (`DB.getSecondary("cctranscripts")`), not a `DatabaseFactory` of its own. That is the only reason the window and `proj cct find` can both work: ArcadeDB takes an exclusive directory lock, so two servers meant the CLI was locked out whenever the window was open. Queries ride the existing daemon route by being registered in `QueryCommands`.
- **`HumanTurns` exists because one typed prompt is written to the transcript several times.** Not adjacent, not sharing a parentUuid, sometimes with no parentUuid at all. The rule is on the text: a human turn whose text is a prefix of a later one is a draft of it. Removing this brings the duplicates straight back.
- **There is no thinking scope, deliberately.** Every thinking block in the store holds `"thinking":""` - claude-code keeps only the encrypted signature. F9 cycles two scopes, not three.

`cct/ui/CctApp` runs the pane in a window of its own. It is not how this ships; it is there so the pane can be started and killed without a daemon in the way.

## The global summon key

`RegisterHotKey`, not a keyboard hook - a hook can only observe a key, so anything Windows already uses would fire twice. Default `win+\`. Never use a double-tapped Shift: five Shift presses is the Windows Sticky Keys shortcut and two summons is already four of them.
