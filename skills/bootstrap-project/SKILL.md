---
name: bootstrap-project
description: Detect the stack, write the descriptor, materialize recipes and scaffolding
disable-model-invocation: true
---

# bootstrap-project

Role: the one-time setup. Detects the stack, writes
`.claude/project.edn`, materializes the active write-<lang> recipes,
scaffolds the artifact, run-state, and hooks directories, drops the
project CLAUDE.md, and wires the spine adapter and the runtime
projections.

## Prerequisites

Run in the project repository root, once per project (or after a stack
change). No descriptor exists yet, or the existing one is being
regenerated.

## Procedure

Follow `docs/design.md` section 8.2. The descriptor schema is
`skills/shared/references/project-descriptor.md`; the spine interface is
`skills/shared/references/spine.md`; the hook templates and their
runtime mapping are documented in `hooks/README.md`.

1. **Detect the stack.** Scan for language markers: `deps.edn` or
   `project.clj` (Clojure), `build.zig` or `.zig-version` (Zig),
   `mix.exs` (Elixir), `CMakeLists.txt` or `Makefile` or `*.h`
   alongside `*.c` (C). Detect the VCS (`.jj` resolves to `:jj`, else
   `.git` to `:git`); jj-first is the default. Detect the UI surface
   (frontend markers). Detect the spine level from `bb` presence on
   PATH: `:babashka` when present, else `:thin` (the level for C, Zig,
   or Elixir without bb), else `:none` when the project opts out.
   Derive the working dir from the repo name.
2. **Elicit the gaps.** Ask one batch of at most three questions for
   what the detector cannot decide: the architecture pattern (confirm
   Functional Core / Imperative Shell or name a divergence), the native
   edge (is there a boundary between the languages), and the module
   roots (the detector proposes, the author confirms or corrects). Ask
   in the same batch which `:hooks` to arm and whether to confirm both
   `:runtimes`. Apply the descriptor defaults when the author gives no
   answer.
3. **Write the descriptor.** Write `.claude/project.edn` (committed, not
   gitignored) with the detected and elicited fields. Every field has a
   default; a minimal descriptor is `{}`.
4. **Materialize the active recipes.** Copy the toolkit masters for the
   write-<lang> recipes named in `:languages` into the project's
   `.claude/skills/`. Only the active subset lands. The four curated
   masters are write-c, write-zig, write-clj, write-elixir. A language
   outside the bounded stack lands nothing here; it is added later via
   add-language.
5. **Scaffold the artifact and run-state directories.** Create
   `.claude/artifacts/` (planning, decisions, project, ops, and adr per
   the descriptor's `:adr :store`), `.claude/runs/` (ephemeral,
   gitignored), and the spine working dir (`:spine :working-dir`,
   gitignored).
6. **Scaffold the hooks.** Copy the hook templates named in `:hooks`
   (`format-on-write`, `deny-secrets`, and `require-tests-before-land` when
   armed) from the toolkit's `hooks/` into `.claude/hooks/`. Preserve the
   executable bit. The hook scripts are the master form; `hooks/README.md`
   is their contract.
7. **Drop the CLAUDE.md.** Copy `templates/CLAUDE.md` into the project
   root as `CLAUDE.md`. Fill the `{{placeholders}}` from the descriptor:
    project name, primary and secondary languages, the domain guidelines
    path,
   the cheap, wave, and pre-land lane commands, the eval command, the
   spine working dir, and the operational gotchas. The project owns this
   file after bootstrap.
8. **Wire the hooks into the host runtime.** Write the
   `.claude/settings.json` hooks block that maps each armed hook to its
   matcher event per `hooks/README.md`. Scripts live under
   `$CLAUDE_PROJECT_DIR/.claude/hooks/`.
9. **Wire the spine and the runtime projections.** For the detected
   level (`:babashka`, `:thin`, `:none`), wire the spine adapter so the
   task names in `spine.md` answer. For every runtime in `:runtimes`:
   run the `opencode-sync` spine task to project the masters into
   `.opencode/agent/`; write the OpenCode permission rules derived from
   the armed hooks into `opencode.json` (the deny list from
    `deny-secrets`, the formatter config from `format-on-write`, and the
    optional plugin module path for `require-tests-before-land`); run
    `opencode-check` to confirm the derived form is green against the masters.

## Boundaries

Run once per project (or after a stack change). It writes only the
descriptor, the materialized recipe subset, the scaffolded directories,
the hooks, the settings hooks block, the CLAUDE.md, the spine wiring,
and the runtime projections under `.opencode/` and `opencode.json`. It
does not write project code, does not pick a feature, does not plan. It
is the one setup valve; every later retune goes through the add-*
meta-skills, which amend the descriptor. Atoms dispatched: the
`opencode-sync` and `opencode-check` spine tasks for the runtime
projections. The hook templates in `hooks/`, the CLAUDE.md skeleton in
`templates/`, and the write-<lang> masters are sources this skill copies
from.

## Returns

One line: the descriptor path, the materialized recipe count, the
scaffolded paths, the armed hooks, the detected spine level, and the
runtime projections written.
