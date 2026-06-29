---
name: bootstrap-project
description: Detect the stack, write the descriptor, materialize recipes and scaffolding
disable-model-invocation: true
---

# bootstrap-project

Role: the one-time setup. Detect the stack, write
`~/.agentic-sdk/<project>/project.edn`, and hand the mechanical work to
the `agentic` CLI. The CLI builds the project home, symlinks the
masters from the shared SDK source into `.claude/`, runs `opencode-sync`
to generate `.opencode/agent/`, copies `CLAUDE.md`, drops three
symlinks in the project root, and writes `.git/info/exclude` entries so
the project repo stays completely clean.

## Prerequisites

Run in the project repository root, once per project (or after a stack
change). The SDK source is on PATH at `bin/agentic`, with
`$AGENTIC_SDK_SRC` pointing at the shared install.

## Procedure

Follow `docs/design.md` section 8.2. The descriptor schema is
`skills/shared/references/project-descriptor.md`; the spine interface is
`skills/shared/references/spine.md`; the hook templates and their
runtime mapping are documented in `hooks/README.md`.

1. **Detect the stack.** Scan cwd for language markers: `deps.edn` or
   `project.clj` (Clojure), `build.zig` or `.zig-version` (Zig),
   `mix.exs` (Elixir), `CMakeLists.txt` or `Makefile` or `*.h`
   alongside `*.c` (C). Detect the VCS (`.jj` resolves to `:jj`, else
   `.git` to `:git`); jj-first is the default. Detect the UI surface
   (frontend markers). Derive the project name from the basename of the
   canonical cwd, which selects the home at
   `~/.agentic-sdk/<project>/`.
2. **Elicit the gaps.** Ask one batch of at most three questions for
   what the detector cannot decide: the architecture pattern (confirm
   Functional Core / Imperative Shell or name a divergence), the native
   edge (is there a boundary between the languages), the module roots
   (the detector proposes, the author confirms or corrects). Ask in the
   same batch which `:hooks` to arm and whether to confirm both
   `:runtimes`. Apply the descriptor defaults when the author gives no
   answer.
3. **Write the descriptor.** Write
   `~/.agentic-sdk/<project>/project.edn` with the detected and
   elicited fields. Every field has a default; a minimal descriptor is
   `{}`.
4. **Run `agentic setup`.** The CLI does the filesystem work: creates
   the project home structure (the `state/` and `artifacts/` dirs);
   writes `.claude/` with skill and agent symlinks from
   `$AGENTIC_SDK_SRC`, copies the armed hooks from
   `$AGENTIC_SDK_SRC/hooks/`, and generates `.claude/settings.json`
   from the descriptor; runs `opencode-sync` to generate
   `.opencode/agent/`; copies `CLAUDE.md` from `$AGENTIC_SDK_SRC/templates/`;
   creates three symlinks in the project root (`.claude`, `.opencode`,
   `CLAUDE.md`) pointing into the project home; and appends the
   exclude entries to `.git/info/exclude` so the symlinks stay invisible
   to git.
5. **Verify.** Run `agentic status` and confirm the home path, the
   descriptor, the linked adapters, and the armed hooks all read green.

## Boundaries

Run once per project (or after a stack change). Writes only the
descriptor at `~/.agentic-sdk/<project>/project.edn`. Dispatches
`agentic setup` for the mechanical filesystem work and `agentic status`
for verification. Does not touch the project repo beyond the three
symlinks and the exclude entries the CLI writes. Does not write project
code, pick a feature, or plan. It is the one setup valve; every later
retune goes through the add-* meta-skills, which amend the descriptor.
Atoms dispatched: `agentic setup`, `agentic status`. Sources read from:
`$AGENTIC_SDK_SRC/skills/`, `$AGENTIC_SDK_SRC/agents/`,
`$AGENTIC_SDK_SRC/hooks/`, and `$AGENTIC_SDK_SRC/templates/`.

## Return

One line: the project name, the home path, the descriptor path, the
armed hooks, and the verification verdict from `agentic status`.
