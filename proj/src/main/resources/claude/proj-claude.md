<!-- Written by `proj claude install`. `proj claude install --undo` removes it, `proj claude status` says whether it is current. -->
# proj: Project Requirement Prompts (PRPs)

`proj` is on PATH and manages PRPs, the requirement documents in a project's `prp/` folder. Before ANY work on a PRP (reading one, creating one, continuing or closing one), run `proj prp guide` and follow what it prints: it is the current convention and replaces any older PRP instructions. `proj prp --help` lists every command.

- NEVER run `proj prp agent` (without `--print`) unless the user said yes to that exact PRP and project in this conversation: the agent it starts can act without permission prompts.
