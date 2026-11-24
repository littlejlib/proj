# PRP conventions

This text is the current convention for working on a PRP. It replaces any older "Usage Guide" block that a PRP file still carries, and any older instructions about PRPs: where they differ, follow this. There is NO `00-prp.index.md` to maintain, NO `/export` slash command for logs, and NO status markdown file to write.

A PRP (Project Requirement Prompt) is one requirement of a project. Its files are in the project's `prp/` folder.

## Numbers and names

- A PRP number is 4 symbols of base34: 0-9 then A-Z without I and O, upper case, counted 0001, 0002 .. 0009, 000A .. 000Z, 0010 ... proj accepts any width and case (`7`, `07`, `0007`, `a3`).
- The project's short name is in `prp/proj.json` (`name`, with a 3-letter and a 4-letter alias). A ticket is `<name>-<number>`, shown with leading zeros dropped to the width the project needs: `billing-7`, or `billing-07` once the project has more than 33 PRPs. Terminal tabs are named `<ticket>-<task>`.
- An old 2-character number (from before 2026-11-21) is found with `proj prp find was:<old number>`; each PRP's meta.json keeps `was`.

## The files of PRP 0007 (the first 4 symbols are the PRP, the next 2 the slot)

- `000700-<name>.md`: the requirement, written by the user. Do not edit it. Only proj writes into it (the front matter at the top, and `<user-instructions>` blocks added by `proj prp instructions`; those are the user's own words, inserted by a tool, not an AI edit). A PRP is never renamed to close it: closing is `prp_status: closed` in the front matter.
- Its front matter: `prp_number` (plain number), `prp_number_b34str` ("0007"), `prp_name`, `prp_short_name` (names the terminal tab: `<ticket>-<short name>`), `prp_3_letter_name` (unique in the project), `prp_uuid`, `prp_status`, `created_utc`, `last_worked_on_utc`, `conventions`. proj suggests the two short names when it creates the PRP; the user may rename them in the file. `prp_status` is one of `new`, `in_progress`, `in_review` (built, waiting for the user to confirm), `on_hold` (paused by choice), `blocked` (waiting on something outside), `closed`, `cancelled` (dropped without doing it); anything else is refused. The two times are quoted UTC (`"2026-11-22T06:35:00Z"`). proj sets all three itself: a note, a session start or resume, or a status change moves the status where it belongs and stamps `last_worked_on_utc` (at most hourly); an older PRP gets the keys the next time proj touches it. Set the status by hand with `proj prp set-status <number> <value>`. A PRP the user has open in notepadfx is changed only through notepadfx, never on disk.
- `000701.status.jsonl`: the PRP's timeline (what was done, tested, decided), one line per note, each naming the session that wrote it. Write to it ONLY with `proj prp note`; read it with `proj prp status 7`. An older `000701.status.md` may exist: read it as history, never add to it.
- `000701.meta.json`: summary and relations to other PRPs: `proj prp summary`, `proj prp relate`.
- `000702-<what>.md`, `000703-<what>.md` ...: your own notes, reports, drafts and data for this PRP, in the next free slot. Put such files here, never in the project root.
- `0007ZZ-01.logs.jsonl`: session logs, written by `proj prp logs`.
- Project files: `workplan.jsonl` (work items, `proj prp plan`), `index.jsonl` (a cache, `proj prp index`; never edit), `proj.json`, `renumber-map.jsonl` (old to new names).

## Before starting

1. Read the PRP file (and an older status markdown file if there is one).
2. Run `proj prp recall <number>` and read the output: where the PRP stands and what was said in the previous session, within a token budget.
3. Ask the user the open questions before building. Write the answers into the timeline (`-k decision`) so they are not asked again.

## While working

- Record progress with `proj prp note <number> "<one line>" -k <kind>`. Kinds: note, decision, scope, question, handover, built, tested, stalled, closed, and confirmed ONLY after the user said it works. At most 280 characters: put any detail in a sub-file and add `--ref <file>`. Write rarely and tersely. Compiling or passing tests is not success; only the user confirms.
- When the user changes or widens the scope, record it with `-k scope`.
- Work items of the project: `proj prp plan add "<title>" -t todo|discuss|confirm -p <number>`, `proj prp plan list`, `proj prp plan set <id> -s done`.
  - Importance: `--tag critical|infra|extra`. Waits for: `--after 3,billing#5` (an id here, or `Project#id` in another project); `--blocks billing#7` makes that item wait for this one.
  - Never run together: `--uses git:billing-core` (a shared resource: a repository, a document, an account; `account:free*2` lets two at once).
  - More on an item: `--detail "<one to three sentences>"` and `--ref <file, commit or url>` (repeatable); `plan set` replaces both.
  - Every command explains its own options: `proj prp plan add --help`.
  - Starting an item: `proj prp plan set <id> -s doing` FIRST, before working on it. If proj refuses because a live item holds a resource, stop and report; do not use `--force`. Set `done` (or `open`) when you stop.
  - The plan of several projects as one graph: `proj prp plan report --with billing,ledger --goal billing:31 --format md`; for filtering, `--format json --jq '<program>'` (no separate jq needed). The user reads and answers it at `http://127.0.0.1:8688/report?with=...`; his answers arrive as items set done with `reply` and source `user ... (web)`, and as decision notes on the PRP.
- Other PRPs: `proj prp find <words | ticket | uuid>`, `proj prp status <number>`.
- Keep confidential data out of PRP files and notes (prefer paths relative to the user's home). Keep the `prp/` folder out of git.

## Splitting work

- A long instruction that touches several projects, or holds several separate pieces of work, is split into PRPs, one per owning project: say so and propose the split before acting on it. Create each with `proj prp new "<name>" --dir <project>`. Each child PRP carries the context (what it serves, who waits for it) and the user's instruction VERBATIM in a fenced block, never a paraphrase. The parent's timeline gets a short handover note. State the order when one strand needs another's output.
- A defect found in another project of the user (a library, a tool, a build plugin) gets a PRP in the project that owns it, in the same session: what happened with the exact error, how to reproduce it, who else is affected, the workaround in place and where to delete it later, and what proves the fix (a test that fails on the old version and passes on the new). Mark the workaround in the consuming code with the PRP it waits for.

## Agents

- NEVER run `proj prp agent` (without `--print`) unless the user said yes to that exact PRP and project in this conversation. The agent it starts can act without permission prompts, in a tab nobody may be watching. Approval for one child is not approval for its siblings. `--print` shows the command and launches nothing. Settle open questions in the PRP before a launch: a child cannot ask the user.
- Sub-agents (inside this session) suit short lookups whose answer comes straight back. Long or batch work that should survive this session goes to a child PRP. Either way, the worker appends its output to a file after every item, so a stop leaves everything done so far on disk.
- A child that has reported done, been checked, and written its logs can be closed to free memory, only when the user agreed to that in advance.

## Ending a cycle (the context is nearly full, or the work is done)

Run BOTH, for real, every time: `proj prp logs <number>` and `proj prp instructions <number>`. Never run only one, never only `--dry-run`. Both use this session (`CLAUDE_CODE_SESSION_ID`) by default. To close the PRP: `proj prp note <number> "<why>" -k closed`, which sets `prp_status: closed` in its head. Do not rename the file.

`proj prp --help` and `proj prp <verb> --help` list every command.
