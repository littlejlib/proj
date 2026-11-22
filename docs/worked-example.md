# Worked example: one instruction, three PRPs, two projects, agents in their own tabs

This is the use case `proj prp agent` was built for, and it is worth writing down because it is not
obvious from the flag list.

**The situation.** A long, elaborate instruction arrives in the middle of a conversation that is
about something else — in this case a billing problem, where the instruction was actually four things:
remove a feature, change how a different tool exports its data, change how a third thing renders,
and migrate old data. Three separate codebases, one of them not a codebase at all.

The old answer was to work through it in the open conversation until the context window ran out.

**What to do instead.** Split it by the project that owns each piece, one PRP per project, and let
each run in its own agent:

```
prp/17                     the matter - correspondence and strategy, no code
  └── billing-03        storage shape, data migration, rendering
        └── exporter-02  make the upstream tool emit the structure the renderer needs
```

Then start them, each in a new terminal tab, from wherever you happen to be:

```
proj prp new "rich_structured_transcript_export" --dir C:\work\exporter
proj prp agent 2 --dir C:\work\exporter --print   # see the command first
proj prp agent 2 --dir C:\work\exporter           # opens a named tab and runs
```

`--dir` is what makes this work across projects: the agent's working directory is the PROJECT root
that owns the work, not the directory you typed the command in. The tab is named after the PRP key,
so `billing-03-call` and `exporter-02-rich` are distinguishable at a glance, and each carries
its own `--session-id`, so `proj prp resume` can pick any of them up later.

**Three rules that make it hold together.**

**1. The child PRP carries the user's words verbatim, not a summary.** A paraphrase loses the
reasoning, and the child agent has none of the conversation the instruction arrived in. Quote the
relevant part in a fenced block and say where it came from.

**2. Every child names its parent, and hands a few lines back up when done** — into the parent's
`NN-prp.status.md`. What now exists, one small example, what the parent must not assume. A handover
is not a copy of the work.

**3. State the order.** Where one PRP cannot start until another produces something, say so in both
files. Above, the renderer cannot draw a structure that is not being emitted yet, so the upstream
PRP goes first — while the data migration, which needs nothing new, runs in parallel.

**Why bother, when a coding agent can already spawn sub-agents.** A sub-agent's context dies with
the conversation. This is the same fan-out with a file trail: each strand has a written requirement,
a status file, a resumable session and a named tab, it survives a context limit or a machine restart,
and it crosses project boundaries — which an in-process sub-agent cannot do, because the thing that
needs changing lives in another repository.

### ⚠ Read this before you automate the launch

`proj prp agent` starts the agent with `agentCommand`, whose `%FLAGS%` placeholder resolves to
`--dangerously-skip-permissions` when **`agentSkipPermissions`** is true — which is the default, and
is what it is set to on this machine. **That means the child does not ask before it acts.** It can
write, delete, rebuild and run anything inside that project, unsupervised, in a tab nobody is
necessarily watching — and the whole point of the pattern above is that you start several at once,
in repositories other than the one you are sitting in.

The permission mode is fine in a single conversation a human is reading. This pattern is the case
where it stops being fine, because it removes the human from the loop at exactly the moment the
blast radius multiplies. The risk comes from the fan-out, not from the flag.

**So if you are driving this from a coding agent rather than typing it yourself: the agent must ask
first, naming each PRP and the project it will run in, and wait for a yes.** "Use PRPs for this" is
not authorisation to launch. Approval for one child is not approval for its siblings.

Everything up to the launch is free and reversible — writing the PRP files, and `--print`, which
shows the exact command, tab name and session id without starting anything. Do all of that, show it,
then launch. And settle the open decisions IN the PRP beforehand: a child agent cannot ask the user,
so anything ambiguous gets guessed, and a guess in another repository is expensive to unpick.

**One switch turns it off:** `proj config set agentSkipPermissions false`. `%FLAGS%` then resolves
to nothing and every child prompts like an ordinary session. It is a placeholder precisely so this
can be changed without hand-editing a command line and getting the quoting wrong. Worth doing for a
run you are not going to sit and watch.

